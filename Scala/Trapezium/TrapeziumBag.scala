import ox.scl._

/** Class to calculated the integral of f from a to b using n intervals.  The
  * calculation uses using nWorkers workers threads, and nTasks tasks.
  * Buffered channels are used if buffChan is true. */
class TrapeziumBag(
  f: Double => Double, a: Double, b: Double, n: Long, 
  nWorkers: Int, nTasks: Int, buffering: Int = -1)
    extends TrapeziumT(f, a, b){
  require(0 < nTasks && nTasks <= n && n/nTasks < (1<<31)-1 )

  /** Type of tasks to send to client.  The Task (left, right, taskSize, delta)
    * represents the task of calculating the integral from left to right,
    * using taskSize intervals of size delta. */
  private type Task = (Double, Double, Int, Double)

  private def mkChan[A: scala.reflect.ClassTag]: Chan[A] = 
    if(buffering > 0) new BuffChan[A](buffering) else new SyncChan[A]

  /** Channel from the controller to the workers, to distribute tasks. */
  private var toWorkers: Chan[Task] = mkChan[Task]

  /** Channel from the workers to the controller, to return sub-results. */
  private val toController: Chan[Double] = mkChan[Double]

  /** A worker, which repeatedly receives arguments from the distributor,
    * estimates the integral, and sends the result to the collector. */
  private def worker = thread("worker"){
    repeat{
      val (left, right, taskSize, delta) = toWorkers?()
      assert(taskSize > 0)
      val result = integral(left, right, taskSize, delta)
      toController!result
    }
  }

  /** A distributor, who distributes tasks to the clients. */
  private def distributor = thread("distributor"){
    // size of each interval
    val delta = (b-a)/n
    // Number of intervals not yet allocated.
    var remainingIntervals = n
    var left = a // left hand boundary of next task
    for(i <- 0 until nTasks){
      // Number of intervals in the next task; the ceiling of
      // remainingIntervals/(nTasks-i).
      val taskSize = ((remainingIntervals-1) / (nTasks-i) + 1).toInt
      assert(taskSize > 0, s"$n; $nTasks")
      remainingIntervals -= taskSize
      val right = left+taskSize*delta
      toWorkers!(left, right, taskSize, delta)
      left = right
    }
    toWorkers.endOfStream
  }

  /** This variable ends up holding the result. */
  private var result = 0.0

  /** A collector, that accumulates the sub-results into result. */
  private def collector = thread("collector"){
    result = 0.0
    for(i <- 0 until nTasks) result += toController?()
  }

  /** The main system. */
  private def system = {
    val workers = || (for (i <- 0 until nWorkers) yield worker)
    workers || distributor || collector
  }

  def apply: Double = { run(system); result } 
  // Note: apply should be called only once.  On a second call, the (new)
  // workers and distributor will throw a ChanClosed exception, but the
  // collector will hang.
}

// =======================================================
