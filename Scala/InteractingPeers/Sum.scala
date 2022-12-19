import ox.scl._

/** The trait specifying the various sum examples.
  * 
  * Each thread calls apply, passing in its value, and gets back the overall
  * sum. */
trait Sum{
  /** Submit value, and receive back overall sum. 
    * @param me the identity of this thread.
    * @param x the value submitted. */
  def apply(me: Int, x: Int): Int
}

// -------------------------------------------------------

/** Implementation of Sum using a controller.  The controller is the thread
  * with identity 0. */
class Centralised(n: Int) extends Sum{
  private val toController = new BuffChan[Int](n-1 max 1)
  private val fromController = new BuffChan[Int](n-1 max 1)

  def apply(me: Int, x: Int): Int = {
    if(me == 0){ // this is the controller
      var sum = x
      // Receive values from other threads
      for(i <- 1 until n){ val w = toController?() ; sum += w }
      // Distribute sum
      for(i <- 1 until n) fromController!sum
      sum
    }
    else{
      toController!x  // submit my value
      fromController?() // get back the result
    }
  }
}

// -------------------------------------------------------

/** Implementation of Sum using the symmetric (fully connected) pattern. */
class Symmetric(n: Int) extends Sum{
  /** Channels to send to nodes, indexed by the receivers' identities. */
  private val toNode = Array.fill(n)(new BuffChan[Int](n-1 max 1))

  def apply(me: Int, x: Int): Int = {
    // Process to send x to other threads.  First version has all threads
    // sending in the same order; the second tries to avoid contention.
    // def sender = thread{ for(i <- 0 until n) if(i != me) toNode(i)!x }
    def sender = thread{ for(i <- 1 until n) toNode((me+i)%n)!x }
    // Variable to hold sum so far
    var sum = x
    // Process to receive from other threads, and accumulate sum
    def receiver = thread{
      for(i <- 1 until n){ val w = toNode(me)?(); sum += w }
    }
    run(sender || receiver)
    sum
  }
}

// -------------------------------------------------------

/** Implementation of Sum using a ring, with a distinguished initiator.
  * The initiator is the thread with identity 0. */
class Ring(n: Int) extends Sum{
  require(n >= 2)

  /** Channels connecting the threads.  Channel chan(i) goes from thread (i-1)
    * mod n to thread i; so thread me inputs on chan(me) and outputs on
    * chan((me+1)%n). */
  private val chan = Array.fill(n)(new BuffChan[Int](1))
 
  def apply(me: Int, x: Int): Int = {
    val in = chan(me); val out = chan((me+1)%n)
    if(me == 0){ // This is the initiator
      // Start the communications going
      out!x
      // Receive sum back, and send it round
      val sum = in?(); out!sum; sum
    }
    else{
      // receive sum so far, and pass on possibly updated sum
      val sum1 = in?(); out!sum1+x
      // receive final sum, and pass it on
      val sum = in?(); if(me != n-1) out!sum
      sum
    }
  } 
}

// -------------------------------------------------------

/** Implementation of Sum using a symmetric ring. */
class RingSym(n: Int) extends Sum{
  /** Channels connecting the threads.  Channel chan(i) goes from thread (i-1)
    * mod n to thread i; so thread me inputs on chan(me) and outputs on
    * chan((me+1)%n).  These channels need to be buffered. */
  private val chan = Array.fill(n)(new BuffChan[Int](1))

  def apply(me: Int, x: Int): Int = {
    val in = chan(me); val out = chan((me+1)%n)
    var sum = x
    out!x                 // send my value round
    for(k <- 1 until n){
      val w = in?()       // receive next value
      sum += w
      out!w               // pass it on
    }
    val w = in?(); assert(x == w) // receive my value back
    sum
  }
}

// -------------------------------------------------------

class Tree(n: Int) extends Sum{
  /** Channels leading up and down the tree.  Each array is indexed by the
    * child's identity. */
  private val up, down = Array.fill(n)(new BuffChan[Int](1))
  
  def apply(me: Int, x: Int) = {
    // Identities of the two children
    val child1 = 2*me+1; val child2 = 2*me+2
    // sum seen so far
    var sum = x
    // Receive sub-sums from both children
    if(child1 < n){ val sum1 = up(child1)?(); sum += sum1 }
    if(child2 < n){ val sum1 = up(child2)?(); sum += sum1 }
    // Send sum to parent, and wait for overall sum to return
    if(me != 0){ up(me)!sum; sum = down(me)?() }
    // Send sum to children
    if(child1 < n) down(child1)!sum
    if(child2 < n) down(child2)!sum
    sum
  }
}
