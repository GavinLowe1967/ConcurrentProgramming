import ox.scl._

/**  Class to calculated the integral of f from a to b using n intervals.  The
  * calculation uses using nWorkers workers threads, and nTasks tasks.
  * Buffered channels are used if buffChan is true.  This version
  * encapsulates the concurrency within objects. */
class TrapeziumBagObjects(
  f: Double => Double, a: Double, b: Double, n: Long, 
  nWorkers: Int, nTasks: Int, buffering: Int = -1, useMonitor: Boolean = false)
    extends TrapeziumT(f, a, b){
  require(0 < nTasks && nTasks <= n && n/nTasks < (1<<31)-1 )

  import TrapeziumBagObjects.Task



  /** A worker, which repeatedly receives tasks from the BagOfTasks, estimates
    * the integral, and adds the result to the Collector. */
  private def worker(bag: BagOfTasks, collector: Collector) = thread{
    repeat{
      val (left, right, taskSize, delta) = bag.getTask
      assert(taskSize > 0)
      val result = integral(left, right, taskSize, delta)
      collector.add(result)
    }
  }

  /** Calculate the integral. */
  def apply: Double = {
    val bag: BagOfTasks = 
      if(useMonitor) new BagOfTasksMonitor(a, b, n, nTasks) 
      else new BagOfTasksChannels(a, b, n, nTasks, buffering)
    val collector: Collector = 
      if(useMonitor) new CollectorMonitor
      else new CollectorChannels(nTasks, buffering)
    val workers = || (for (i <- 0 until nWorkers) yield worker(bag, collector))
    run(workers)
    collector.get
  }
}

// ------------------------------------------------------------------

object TrapeziumBagObjects{
  /** Type of tasks to send to client.  The Task (left, right, taskSize, delta)
    * represents the task of calculating the integral from left to right,
    * using taskSize intervals of size delta. */
  type Task = (Double, Double, Int, Double)
}

// ==================================================================

import TrapeziumBagObjects.Task

/** The bag of tasks object. */
trait BagOfTasks{
  def getTask: Task
}

// ------------------------------------------------------------------

/** The bag of tasks object implemented using channels. */
class BagOfTasksChannels(
  a: Double, b: Double, n: Long, nTasks: Int, buffering: Int)
    extends BagOfTasks{

  /** Channel from the controller to the workers, to distribute tasks. */
  private val toWorkers =
    if(buffering > 0) new BuffChan[Task](buffering) else new SyncChan[Task]

  /** Get a task.
    * @throws Stopped exception if there are no more tasks. */
  def getTask: Task = toWorkers?()

  /** A server process, that distributes tasks. */
  private def server = thread{
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

  // Start the server running
  fork(server)
}

// ------------------------------------------------------------------

/** The bag of tasks object implemented using a monitor. */
class BagOfTasksMonitor(a: Double, b: Double, n: Long, nTasks: Int)
    extends BagOfTasks{
  // size of each interval
  private val delta = (b-a)/n

  // // Number of intervals not yet allocated.
  // private var remainingIntervals = n

  // // left hand boundary of next task
  // private var left = a 

  // Number of tasks so far
  private var i = 0

  /** Boundary of the ith task.  The number of intervals in tasks [0..i). */
  @inline private def boundary(i: Int) = n*i/nTasks

  @inline private def getTask(i: Int): Task = {
    val b1 = boundary(i); val b2 = boundary(i+1)
    val taskSize = b2-b1; val left = a+b1*delta; val right = a+b2*delta
    (left, right, taskSize.toInt, delta)
  }

  /** Get a task.
    * @throws Stopped exception if there are no more tasks. */
  def getTask = {
    var myI = -1
    synchronized{ myI = i; i += 1} // get and increment i
    if(myI < nTasks) getTask(myI)
    else throw new Stopped
  }
      // val taskSize = ((remainingIntervals-1) / (nTasks-i) + 1).toInt
      // assert(taskSize > 0, s"$n; $nTasks")
      // i += 1; remainingIntervals -= taskSize
      // val right = left+taskSize*delta
      // val result = (left, right, taskSize, delta)
      // left = right
      // result
}

// ==================================================================


/** A collector object that receives sub-results from the workers, and adds
  * them up. */
trait Collector{
  /** Add x to the result. */
  def add(x: Double): Unit

  /** Get the result. */
  def get: Double
}

// ------------------------------------------------------------------

/** A collector object that receives sub-results from the workers, and adds
  * them up, using channels. */
class CollectorChannels(nTasks: Int, buffering: Int) extends Collector{
  /** Channel from the workers to the controller, to return sub-results. */
  private val toController =
    if(buffering > 0) new BuffChan[Double](buffering) else new SyncChan[Double]

  /** Channel that sends the final result. */
  private val resultChan = new SyncChan[Double]

  /** A collector, that accumulates the sub-results. */
  private def server = thread{
    var result = 0.0
    for(i <- 0 until nTasks) result += toController?()
    resultChan!result
  }

  // Start the server running
  fork(server)

  /** Add x to the result. */
  def add(x: Double) = toController!x

  /** Get the result. */
  def get: Double = resultChan?()
}

// ------------------------------------------------------------------

class CollectorMonitor extends Collector{
  /** The sum so far. */
  private var sum = 0.0

  /** Add x to the result. */
  def add(x: Double) = synchronized{ sum += x }

  /** Get the result. */
  def get = synchronized{ sum }

}
