package org.apache.spark.examples.h2o

import org.apache.spark.h2o.H2OContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkContext, SparkConf}
import water.util.Log

object H2OTest {

  def main(args: Array[String]) {

    // Create Spark context which will drive computation
    val localDebug = true
    val sc = createSparkContext(if (localDebug)"local" else "spark://localhost:7077" )
    // Start H2O-in-Spark
    water.H2O.main(args)
    water.H2O.waitForCloudSize(1/*One H2ONode to match the one Spark local-mode worker*/,1000)

    // Load raw data
    val parse = ProstateParse
    val rawdata = sc.textFile("h2o-examples/smalldata/prostate.csv",2)
    // Parse raw data per line and produce
    val sqlContext = new SQLContext(sc)
    import sqlContext._ // import implicit conversions
    val table = rawdata.map(_.split(",")).map(line => parse(line))
    table.registerTempTable("prostate")

    // Map data into H2O frame and run an algorithm
    val hc = new H2OContext(sc)

    // Register RDD as a frame which will cause data transfer
    //  - This needs RDD -> H2ORDD implicit conversion, H2ORDDLike contains registerFrame
    val h2oFrame = hc.createH2ORDD(table, "prostate.hex")

    // Stop Spark local worker; stop H2O worker
    sc.stop()
    water.H2O.exit(0)
  }

  private def createSparkContext(sparkMaster:String = null): SparkContext = {
    val local = sparkMaster == null
    // Create application configuration
    val master = if (local) "local" else sparkMaster
    val conf = new SparkConf()
      //.setMaster(master)
      //.setAppName("H2O Integration Example")
      //.set("spark.executor.memory", "1g")
    //if (!local) // Run 'sbt assembly to produce target/scala-2.10/h2o-sparkling-demo-assembly-1.0.jar
    //  conf.setJars(Seq("h2o-examples/target/spark-h2o-examples_2.10-1.1.0-SNAPSHOT.jar"))

    Log.info("Creating " + (if (local) "LOCAL" else "REMOTE ("+master+")") + " Spark context." )
    new SparkContext(conf)
  }
}

/** Prostate schema definition. */
case class Prostate(id      :Option[Int],
                    capsule :Option[Int],
                    age     :Option[Int],
                    race    :Option[Int],
                    dpros   :Option[Int],
                    dcaps   :Option[Int],
                    psa     :Option[Float],
                    vol     :Option[Float],
                    gleason :Option[Int])

/** A dummy csv parser for prostate dataset. */
object ProstateParse extends Serializable {
  def apply(row: Array[String]): Prostate = {
    import SchemaUtils._
    Prostate(int(row(0)), int(row(1)), int(row(2)), int(row(3)), int(row(4)), int(row(5)), float(row(6)), float(row(7)), int(row(8)) )
  }
}

/** Simple support for parsing data. */
object SchemaUtils {

  def int  (s:String):Option[Int]     = if (isValid(s)) parseInt(s)   else None
  def float(s:String):Option[Float]   = if (isValid(s)) parseFloat(s) else None
  def str  (s:String):Option[String]  = if (isValid(s)) Option(s)     else None
  def bool (s:String):Option[Boolean] = if (isValid(s)) parseBool(s)  else None

  def parseInt(s:String):Option[Int] =
    try { Option(s.trim().toInt) } catch {
      case e:NumberFormatException => None
    }

  def parseFloat(s:String):Option[Float] =
    try { Option(s.trim().toFloat) } catch {
      case e:NumberFormatException => None
    }

  def parseBool(s:String):Option[Boolean] = s.trim().toLowerCase match {
    case "true"|"yes" => Option(true)
    case "false"|"no" => Option(false)
    case _ => None
  }

  def isNA(s:String) = s==null || s.isEmpty || (s.trim.toLowerCase match {
    case "na" => true
    case "n/a"=> true
    case _ => false
  })

  def isValid(s:String) = !isNA(s)

  //def na[S]:S = null.asInstanceOf[S]
  //def na[S]:Option[S] = None
}

