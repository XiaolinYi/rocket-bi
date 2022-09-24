package co.datainsider.bi.domain.setting

case class ClickhouseConnectionSetting(
    host: String,
    username: String,
    password: String,
    httpPort: Int,
    tcpPort: Int,
    clusterName: String
) {
  def toJdbcUrl: String = s"jdbc:clickhouse://$host:$httpPort"
}