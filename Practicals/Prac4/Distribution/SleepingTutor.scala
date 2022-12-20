// Template for the Sleeping Tutor practical

import ox.scl._

/** The trait for a Sleeping Tutor protocol. */
trait SleepingTutor{
  /** A tutor waits for students to arrive. */
  def tutorWait

  /** A student arrives and waits for the tutorial. */
  def arrive
  
  /** A student receives a tutorial. */
  def receiveTute

  /** A tutor ends the tutorial. */
  def endTeach
}

// =======================================================

import scala.util.Random

object SleepingTutorSimulation{
  private val st: SleepingTutor = new SleepingTutorMonitor

  def student(me: String) = thread("Student"+me){
    while(true){
      Thread.sleep(Random.nextInt(2000))
      println("Student "+me+" arrives")
      st.arrive
      println("Student "+me+" ready for tutorial")
      st.receiveTute
      println("Student "+me+" leaves")
    }
  }

  def tutor = thread("Tutor"){
    while(true){
      println("Tutor waiting for students")
      st.tutorWait
      println("Tutor starts to teach")
      Thread.sleep(1000)
      println("Tutor ends tutorial")
      st.endTeach
      Thread.sleep(1000)
    }
  }

  def system = tutor || student("Alice") || student("Bob")

  def main(args: Array[String]) = system()
}
