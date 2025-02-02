package datainsider.jobworker.service.readers

import datainsider.client.domain.schema.column._
import datainsider.client.util.JsonParser
import datainsider.jobworker.domain.JobStatus
import datainsider.jobworker.domain.SyncMode.{FullSync, IncrementalSync, SyncMode}
import datainsider.jobworker.domain.job.ReportType.ReportType
import datainsider.jobworker.domain.job._
import datainsider.jobworker.domain.source.TikTokAdsSource
import datainsider.jobworker.exception.AlreadyCompletedException
import datainsider.jobworker.exception.BaseException.AlreadyCompletedException
import datainsider.jobworker.repository.reader.factory.TikTokAdsReaderFactory
import datainsider.jobworker.repository.reader.tiktok.{
  ReportParamsInfo,
  TikTokClient,
  TikTokRecordParser,
  TikTokReportReader,
  TikTokTimeRange
}
import datainsider.jobworker.util.ZConfig

import java.io.InputStream

class TikTokAdsReaderTest extends AbstractTestReader {

  val job = TikTokAdsJob(
    orgId = 1,
    jobId = 1,
    sourceId = 1,
    lastSuccessfulSync = 0,
    syncIntervalInMn = 0,
    lastSyncStatus = JobStatus.Init,
    currentSyncStatus = JobStatus.Init,
    destDatabaseName = "test",
    destTableName = "test",
    destinations = Seq(),
    advertiserId = "7174349799003717633",
    tikTokEndPoint = TikTokAdsEndPoint.Report,
    tikTokReport = None,
    lastSyncedValue = None
  )

  val accessToken = "08ff10569e9df1ef6acd22fda27a4328214f2c75"
  val tikTokTimeRange = TikTokTimeRange(start = "2020-12-08", end = "2021-01-29")
  val report = TikTokReport(
    reportType = ReportType.AuctionBasicCampaignEngagement,
    timeRange = tikTokTimeRange
  )
  val source = TikTokAdsSource(id = 1, displayName = "test_source", accessToken = accessToken)
  val factory = new TikTokAdsReaderFactory(apiUrl = ZConfig.getString("tiktok_ads.base_url"))

  test("test create tiktok reader for ads endpoint") {
    val reader = factory.create(source, job.copy(tikTokEndPoint = TikTokAdsEndPoint.Ads))
    val columns = reader.detectTableSchema().columns
    while (reader.hasNext()) {
      val record = reader.next(columns)
      ensureRecord(columns, record)
    }
  }

  test("test create tiktok reader for campaign endpoint") {
    val reader = factory.create(source, job.copy(tikTokEndPoint = TikTokAdsEndPoint.Campaigns))
    val columns = reader.detectTableSchema().columns
    while (reader.hasNext()) {
      val record = reader.next(columns)
      ensureRecord(columns, record)
    }
  }

  test("test create tiktok reader for adgroup endpoint") {
    val reader = factory.create(source, job.copy(tikTokEndPoint = TikTokAdsEndPoint.AdGroups))
    val columns = reader.detectTableSchema().columns
    while (reader.hasNext()) {
      val record = reader.next(columns)
      ensureRecord(columns, record)
    }
  }

  test("test create tiktok reader for report endpoint for full sync") {
    val reader =
      factory.create(source, job.copy(tikTokEndPoint = TikTokAdsEndPoint.Report, tikTokReport = Some(report)))
    val columns = reader.detectTableSchema().columns
    var reading = true
    while (reader.hasNext() && reading) {
      try {
        val record = reader.next(columns)
        ensureRecord(columns, record)
      } catch {
        case e: AlreadyCompletedException => reading = false
      }
    }
  }

  test("test create tiktok reader for report endpoint for incremental sync") {

    val reader =
      factory.create(
        source,
        job.copy(tikTokEndPoint = TikTokAdsEndPoint.Report, syncMode = IncrementalSync, tikTokReport = Some(report))
      )
    val columns = reader.detectTableSchema().columns
    var reading = true
    while (reader.hasNext() && reading) {
      try {
        val record = reader.next(columns)
        ensureRecord(columns, record)
      } catch {
        case e: AlreadyCompletedException => reading = false
      }
    }
  }

  test("test read all") {
    val reports = Seq(
      ReportType.ReservationBasicAdData,
      ReportType.ReservationBasicAdEngagement,
      ReportType.ReservationBasicAdVideoPlay,
      ReportType.ReservationBasicAdgroupData,
      ReportType.ReservationBasicAdgroupEngagement,
      ReportType.ReservationBasicAdgroupVideoPlay,
      ReportType.ReservationBasicCampaignData,
      ReportType.ReservationBasicCampaignEngagement,
      ReportType.ReservationBasicCampaignVideoPlay,
      ReportType.AuctionBasicAdData,
      ReportType.AuctionBasicAdEngagement,
      ReportType.AuctionBasicAdInAppEvent,
      ReportType.AuctionBasicAdOnsiteEvent,
      ReportType.AuctionBasicAdPageEvent,
      ReportType.AuctionBasicAdVideoPlay,
      ReportType.AuctionBasicAdgroupData,
      ReportType.AuctionBasicAdgroupEngagement,
      ReportType.AuctionBasicAdgroupInAppEvent,
      ReportType.AuctionBasicAdgroupOnsiteEvent,
      ReportType.AuctionBasicAdgroupPageEvent,
      ReportType.AuctionBasicAdgroupVideoPlay,
      ReportType.AuctionBasicCampaignData,
      ReportType.AuctionBasicCampaignEngagement,
      ReportType.AuctionBasicCampaignInAppEvent,
      ReportType.AuctionBasicCampaignOnsiteEvent,
      ReportType.AuctionBasicCampaignPageEvent,
      ReportType.AuctionBasicCampaignVideoPlay,
      ReportType.AuctionBasicAdvertiserData,
      ReportType.AuctionBasicAdvertiserEngagement,
      ReportType.AuctionBasicAdvertiserInAppEvent,
      ReportType.AuctionBasicAdvertiserInteractiveAddOn,
      ReportType.AuctionBasicAdvertiserLive,
      ReportType.AuctionBasicAdvertiserOnsiteEvent,
      ReportType.AuctionBasicAdvertiserPageEvent,
      ReportType.AuctionBasicAdvertiserVideoPlay,
      ReportType.AuctionBasicAdgroupSkan,
      ReportType.AuctionBasicAdSkan,
      ReportType.AuctionBasicCampaignSkan,
      ReportType.AuctionBasicAdvertiserSkan
    )

    def fullSyncTest(reportType: ReportType): Unit =try {
      val reader =
        factory.create(
          source,
          job.copy(tikTokEndPoint = TikTokAdsEndPoint.Report, tikTokReport = Some(report.copy(reportType = reportType)))
        )
      val columns = reader.detectTableSchema().columns
      var reading = true
      while (reader.hasNext() && reading) {
        try {
          val record = reader.next(columns)
          ensureRecord(columns, record)
        } catch {
          case e: AlreadyCompletedException => reading = false
        }
      }
    } catch {
      case e:Throwable=>println(s"${e.getMessage}::::${reportType}")
    }

    reports.map(reportType => fullSyncTest(reportType))

  }

}
