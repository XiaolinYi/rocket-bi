package datainsider.lakescheduler.repository

import com.twitter.util.Future
import datainsider.jobscheduler.client.JdbcClient
import datainsider.jobscheduler.domain.Ids.RunId
import datainsider.jobscheduler.domain.job.JobStatus.JobStatus
import datainsider.jobscheduler.domain.request.SortRequest
import datainsider.jobscheduler.repository.MySqlSchemaManager
import datainsider.lakescheduler.domain.LakeJobHistory
import datainsider.lakescheduler.domain.job.LakeJobStatus

import java.sql.ResultSet
import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer

trait LakeHistoryRepository {

  def insert(orgId: Long, jobHistory: LakeJobHistory): Future[RunId]

  def delete(id: RunId): Future[Boolean]

  /**
    * only update metadata info: lastRunTime, totalRunTime, runStatus
    * @param jobHistory new history to replace old history (same id)
    * @return
    */
  def update(jobHistory: LakeJobHistory): Future[Boolean]

  def get(orgId: Long, keyword: String, from: Int, size: Int, sorts: Seq[SortRequest]): Future[Seq[LakeJobHistory]]

  def get(id: RunId): Future[Option[LakeJobHistory]]

  def count(orgId: Long, keyword: String): Future[Long]

  def getWith(withStatus: Seq[JobStatus], from: Int, size: Int): Future[Seq[LakeJobHistory]]
}

class MysqlLakeJobHistoryRepository @Inject() (
    val client: JdbcClient,
    val dbName: String,
    val tblName: String,
    val requiredFields: List[String]
) extends MySqlSchemaManager
    with LakeHistoryRepository {
  override def get(id: RunId): Future[Option[LakeJobHistory]] =
    Future {
      val query =
        s"""
           |select *
           |from $dbName.$tblName
           |where id = ?;
           |""".stripMargin

      client.executeQuery(query, id)(toHistories).headOption
    }

  override def get(orgId: Long, keyword: String, from: Int, size: Int, sorts: Seq[SortRequest]): Future[Seq[LakeJobHistory]] =
    Future {
      val orderBy =
        if (sorts.nonEmpty) "order by " + sorts.map(toOrderBy).mkString(",")
        else ""
      val query = s"select * from $dbName.$tblName where organization_id = ? and job_name like ? $orderBy limit ? offset ?;"
      client.executeQuery(query, orgId, s"%$keyword%", size, from)(toHistories)
    }

  override def update(history: LakeJobHistory): Future[Boolean] =
    Future {
      val query =
        s"""
           |update $dbName.$tblName
           |set yarn_app_id = ?, start_time = ?, updated_time = ?, end_time = ?, job_status = ?, message = ?
           |where id = ?;
           |""".stripMargin

      client.executeUpdate(
        query,
        history.yarnAppId,
        history.startTime,
        history.updatedTime,
        history.endTime,
        history.jobStatus.toString,
        history.message,
        history.runId
      ) > 0
    }

  override def getWith(withStatuses: Seq[JobStatus], from: Int, size: Int): Future[Seq[LakeJobHistory]] =
    Future {
      val questionMarkHolder = List.fill(withStatuses.length)("?").mkString(",")
      val query =
        s"""
           |select * from $dbName.$tblName
           |where job_status in ($questionMarkHolder)
           |limit $size offset $from;
           |""".stripMargin

      client.executeQuery(query, withStatuses.map(_.toString): _*)(toHistories)
    }

  /**
    * @param jobHistory history to be inserted, pass a dummy id, id is auto generated by mysql
    * @return
    */
  override def insert(orgId: Long, jobHistory: LakeJobHistory): Future[RunId] =
    Future {
      val query =
        s"""
           |insert into $dbName.$tblName
           |(organization_id, job_id, job_name, yarn_app_id, start_time, updated_time, end_time, job_status, message)
           |values (?, ?, ?, ?, ?, ?, ?, ?, ?)
           |""".stripMargin

      client.executeInsert(
        query,
        orgId,
        jobHistory.jobId,
        jobHistory.jobName,
        jobHistory.yarnAppId,
        jobHistory.startTime,
        jobHistory.updatedTime,
        jobHistory.endTime,
        jobHistory.jobStatus.toString,
        jobHistory.message
      )
    }

  override def delete(id: RunId): Future[Boolean] =
    Future {
      val query = s"delete from $dbName.$tblName where id = ?;"
      client.executeUpdate(query, id) > 0
    }

  private def toHistories(rs: ResultSet): Seq[LakeJobHistory] = {
    val histories = ArrayBuffer.empty[LakeJobHistory]
    while (rs.next()) {
      val history = LakeJobHistory(
        runId = rs.getLong("id"),
        jobId = rs.getLong("job_id"),
        jobName = rs.getString("job_name"),
        yarnAppId = rs.getString("yarn_app_id"),
        jobStatus = LakeJobStatus.withName(rs.getString("job_status")),
        startTime = rs.getLong("start_time"),
        updatedTime = rs.getLong("updated_time"),
        endTime = rs.getLong("end_time"),
        message = rs.getString("message")
      )
      histories += history
    }
    histories
  }

  override def createTable(): Future[Boolean] =
    Future {
      val query =
        s"""
           |create table if not exists $dbName.$tblName (
           |organization_id INT,
           |id BIGINT AUTO_INCREMENT PRIMARY KEY,
           |job_id BIGINT,
           |job_name TEXT,
           |yarn_app_id TEXT,
           |start_time BIGINT,
           |end_time BIGINT,
           |updated_time BIGINT,
           |job_status TINYTEXT,
           |message TEXT
           |) ENGINE=INNODB;
           |""".stripMargin

      client.executeUpdate(query) >= 0
    }

  override def count(orgId: Long, keyword: String): Future[Long] =
    Future {
      val query = s"select count(*) from $dbName.$tblName where organization_id = ? and job_name like ?;"
      client.executeQuery(query, orgId, s"%$keyword%")(rs => {
        if (rs.next()) rs.getLong(1)
        else 0L
      })
    }

  private def toOrderBy(sort: SortRequest): String = {
    s"${sort.field} ${sort.order}"
  }
}
