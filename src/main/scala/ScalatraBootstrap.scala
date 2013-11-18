import com.github.m0t0k1ch1.isucon._
import org.scalatra._
import javax.servlet.ServletContext

import org.slf4j.LoggerFactory

import com.mchange.v2.c3p0.ComboPooledDataSource
import scala.slick.driver.MySQLDriver.simple._

import java.io.File
import org.apache.commons.io.FileUtils
import scala.util.parsing.json.JSON

class ScalatraBootstrap extends LifeCycle
{
  val logger = LoggerFactory.getLogger(getClass)

  val cpds = new ComboPooledDataSource
  logger.info("Created c3p0 connection pool")

  override def init(context: ServletContext) {
    val env = {
      val isuconEnv = Option(System.getenv("ISUCON_ENV")).getOrElse("local")
      logger.info("ISUCON_ENV: " + isuconEnv)
      isuconEnv
    }

    val (dbConfig, dataDir) = {
      val dir       = new File("./src/main/resources").getAbsolutePath()
      val file      = dir + "/" + env + ".json"
      val source    = FileUtils.readFileToString(new File(file), "utf-8")
      val appConfig = JSON.parseFull(source).get.asInstanceOf[Map[String, Any]]
      (
        appConfig.get("database").get.asInstanceOf[Map[String, Any]],
        appConfig.apply("data_dir").asInstanceOf[String]
      )
    }

    val db = Database.forURL(
      "jdbc:mysql://" + dbConfig.apply("host") +
        ":" + dbConfig.apply("port").asInstanceOf[Double].toInt +
        "/" + dbConfig.apply("dbname"),
      dbConfig.apply("username").asInstanceOf[String],
      dbConfig.apply("password").asInstanceOf[String]
    )

    context.mount(new Isucon(db, dataDir), "/*")
  }

  def closeDbConnection() {
    logger.info("Closing c3po connection pool")
    cpds.close
  }

  override def destroy(context: ServletContext) {
    super.destroy(context)
    closeDbConnection
  }
}
