package datainsider.ingestion.misc

import com.twitter.inject.Logging
import datainsider.client.exception.UnsupportedError
import datainsider.client.util.ZConfig
import datainsider.ingestion.domain.Types.{DBName, TblName}
import datainsider.ingestion.domain._
import datainsider.ingestion.util.TimeUtils

/**
  * @author andy
  * @since 7/10/20
  */
trait DDLConverter {
  def toCreateShardTableDDL(tableSchema: TableSchema, clusterName: String): String

  def toCreateShardReplacingTableDDL(tableSchema: TableSchema, clusterName: String): String

  def toCreateViewDDL(tableSchema: TableSchema, clusterName: String): String

  def toCreateEtlViewDDL(tableSchema: TableSchema, clusterName: String): String

  def toCreateInMemoryDDL(tableSchema: TableSchema, clusterName: String): String

  def toCreateDistributedTableDDL(tableSchema: TableSchema, clusterName: String): String

  def toCreateReplacingDistributedTableDDL(tableSchema: TableSchema, clusterName: String): String

  def toCreateMaterializedViewDDL(tableSchema: TableSchema, clusterName: String): String

  def toAddColumnDLL(dbName: String, tblName: String, column: Column, clusterName: String): String

  def toUpdateColumnDLL(dbName: String, tblName: String, column: Column, clusterName: String): String

  def toDeleteColumnDDL(dbName: String, tblName: String, columnName: String, clusterName: String): String

  def toInsertSQL(dbName: String, tblName: String, columns: Seq[Column], isApplyEncryption: Boolean = false): String

  def toInsertFromTable(
      fromDbName: String,
      fromTableName: String,
      destDbName: String,
      destTableName: String,
      columns: Seq[Column]
  ): String
}

case class ClickHouseDDLConverter() extends DDLConverter with Logging {

  private def getReplicaPath(dbName: String, tblName: String): String =
    s"/clickhouse/tables/{cluster}/{shard}/$dbName/$tblName"
  private def getReplicaName(): String = "{replica}"

  def toCreateViewDDL(tableSchema: TableSchema, clusterName: String): String = {
    s"""
     |CREATE VIEW IF NOT EXISTS `${tableSchema.dbName}`.`${tableSchema.name}` ON CLUSTER $clusterName
     |AS ${tableSchema.query.get}
     |""".stripMargin
  }

  def toCreateEtlViewDDL(tableSchema: TableSchema, clusterName: String): String = {
    s"""
     |CREATE VIEW IF NOT EXISTS `${tableSchema.dbName}`.`${tableSchema.name}`
     |AS ${tableSchema.query.get}
     |""".stripMargin
  }

  def toCreateInMemoryDDL(tableSchema: TableSchema, clusterName: String): String = {
    s"""
       |CREATE TABLE IF NOT EXISTS `${tableSchema.dbName}`.`${tableSchema.name}`(
       | ${toMultiColumnDDL(tableSchema.columns)}
       |) ENGINE = Memory
       |""".stripMargin
  }

  override def toCreateDistributedTableDDL(tableSchema: TableSchema, clusterName: String): String = {
    val dbName = tableSchema.dbName
    val tblName = tableSchema.name
    s"""
       |CREATE TABLE IF NOT EXISTS `$dbName`.`$tblName` ON CLUSTER $clusterName AS $dbName.${tableSchema.defaultShardTblName}
       |ENGINE = Distributed('$clusterName', $dbName, ${tableSchema.defaultShardTblName}, rand());
       |""".stripMargin
  }

  override def toCreateReplacingDistributedTableDDL(tableSchema: TableSchema, clusterName: String): String = {
    val dbName: String = tableSchema.dbName
    val tblName: String = tableSchema.name
    val shardingKeys: String = tableSchema.orderBys.mkString(", ")
    s"""
       |CREATE TABLE IF NOT EXISTS `$dbName`.`$tblName` ON CLUSTER $clusterName AS $dbName.${tableSchema.defaultShardTblName}
       |ENGINE = Distributed('$clusterName', $dbName, ${tableSchema.defaultShardTblName}, cityHash64(${shardingKeys}));
       |""".stripMargin
  }

  override def toCreateMaterializedViewDDL(tableSchema: TableSchema, clusterName: String): String = {
    s"""
       |CREATE MATERIALIZED VIEW IF NOT EXISTS `${tableSchema.dbName}`.`${tableSchema.name}` ON CLUSTER $clusterName
       |ENGINE = ${getEngineDDL(tableSchema)}
       |ORDER BY ${getOrderByDDL(tableSchema)}
       |POPULATE
       |AS ${tableSchema.query.get}
       |""".stripMargin
  }

  // this only create shard table
  override def toCreateShardTableDDL(tableSchema: TableSchema, clusterName: String): String = {
    val replicaPath = getReplicaPath(tableSchema.dbName, tableSchema.name)
    s"""
       |CREATE TABLE IF NOT EXISTS `${tableSchema.dbName}`.`${tableSchema.defaultShardTblName}` ON CLUSTER $clusterName (
       | ${toMultiColumnDDL(tableSchema.columns)}
       |) ENGINE = ReplicatedMergeTree('$replicaPath', '${getReplicaName()}')
       |${getPrimaryKeyDDL(tableSchema)}
       |${getPartitionByDDL(tableSchema)}
       |ORDER BY ${getOrderByDDL(tableSchema)}
       |""".stripMargin
  }

  override def toCreateShardReplacingTableDDL(tableSchema: TableSchema, clusterName: String): String = {
    val replicaPath = getReplicaPath(tableSchema.dbName, tableSchema.name)

    s"""
       |CREATE TABLE IF NOT EXISTS `${tableSchema.dbName}`.`${tableSchema.defaultShardTblName}` ON CLUSTER $clusterName (
       | ${toMultiColumnDDL(tableSchema.columns)}
       |) ENGINE = ReplicatedReplacingMergeTree('$replicaPath', '${getReplicaName()}')
       |${getPrimaryKeyDDL(tableSchema)}
       |${getPartitionByDDL(tableSchema)}
       |ORDER BY ${getOrderByDDL(tableSchema)}
       |""".stripMargin
  }

  override def toAddColumnDLL(dbName: String, tblName: String, column: Column, clusterName: String): String = {
    s"""
       |ALTER TABLE `$dbName`.`$tblName` ON CLUSTER $clusterName ADD COLUMN IF NOT EXISTS ${toColumnDDLExpr(column)}
       |""".stripMargin
  }

  override def toInsertSQL(
      dbName: String,
      tblName: String,
      columns: Seq[Column],
      isApplyEncryption: Boolean = false
  ): String = {
    val encryptMode = ZConfig.getString("db.clickhouse.encryption.mode")
    val privateKey = ZConfig.getString("db.clickhouse.encryption.key")
    val initialVector = ZConfig.getString("db.clickhouse.encryption.iv")

    val insertedCols: Seq[Column] = flatNestedColumns(columns)

    val valuePlaceHolders: Seq[String] = insertedCols
      .map(c => {
        if (isApplyEncryption && c.isEncrypted) {
          s"encrypt('$encryptMode', ?, unhex('$privateKey'), unhex('$initialVector'))"
        } else "?"
      })

    s"""
       |INSERT INTO `$dbName`.`$tblName` (${insertedCols.map(c => s"`${c.name}`").mkString(", ")})
       |VALUES (${valuePlaceHolders.mkString(", ")})
       |""".stripMargin
  }

  def toInsertFromTable(
      fromDbName: String,
      fromTableName: String,
      destDbName: String,
      destTableName: String,
      columns: Seq[Column]
  ): String = {
    val colNames: Seq[String] = flatNestedColumns(columns).map(c => s"`${c.name}`")

    s"""
       |INSERT INTO `$destDbName`.`$destTableName`(${colNames.mkString(", ")})
       |SELECT ${colNames.mkString(", ")} FROM `$fromDbName`.`$fromTableName`
       |""".stripMargin
  }

  private def flatNestedColumns(columns: Seq[Column]): Seq[Column] = {
    columns
      .flatMap {
        case nestedCol: NestedColumn =>
          nestedCol.nestedColumns.map(c => {
            val flattenColName = s"${nestedCol.name}.${c.name}"
            val flattenDisplayName = s"${nestedCol.displayName}.${c.displayName}"
            c.copyTo(name = flattenColName, displayName = flattenDisplayName)
          })
        case column => Seq(column)
      }
  }

  private def toMultiColumnDDL(columns: Seq[Column]): String = {
    columns
      .map(toColumnDDLExpr(_))
      .filterNot(_ == null)
      .filterNot(_.isEmpty)
      .mkString(",\n")
  }

  private def toColumnDDLExpr(column: Column, defaultEnabled: Boolean = true): String = {

    val columnExpr = column match {
      case column: NestedColumn => toNestedColumnDDLExpr(column)
      case column               => s"`${column.name}` ${getSQLDataTypeExpr(column)}"
    }
    if (defaultEnabled) {
      s"$columnExpr ${getDefaultExpr(column)}".trim
    } else {
      columnExpr
    }
  }

  private def toNestedColumnDDLExpr(column: NestedColumn): String = {
    column.nestedColumns.nonEmpty match {
      case false => ""
      case true =>
        val childColumnDDL = column.nestedColumns
          .filterNot(_.isInstanceOf[NestedColumn])
          .map(toColumnDDLExpr(_, false))
        s"""
           |`${column.name}` Nested(
           | \t${childColumnDDL.mkString(",\n\t")}
           | )
           |""".stripMargin
    }
  }

  private def getSQLDataTypeExpr(column: Column): String = {
    val dataType = column match {
      case column: BoolColumn   => "UInt8"
      case column: Int8Column   => "Int8"
      case column: Int16Column  => "Int16"
      case column: Int32Column  => "Int32"
      case column: Int64Column  => "Int64"
      case column: UInt8Column  => "UInt8"
      case column: UInt16Column => "UInt16"
      case column: UInt32Column => "UInt32"
      case column: UInt64Column => "UInt64"
      case column: FloatColumn  => "Float32"
      case column: DoubleColumn => "Float64"
      case column: StringColumn => "String"
      case column: DateColumn   => "Date"
      case column: DateTimeColumn =>
        if (column.timezone.isDefined)
          s"DateTime('${column.timezone.getOrElse("")}')"
        else
          s"DateTime"
      case column: DateTime64Column =>
        if (column.timezone.isDefined)
          s"DateTime64(3,'${column.timezone.getOrElse("")}')"
        else
          s"DateTime64(3)"
      case arrayColumn: ArrayColumn => s"Array(${getSQLDataTypeExpr(arrayColumn.column)})"
      case column: NestedColumn     => "Nested"
      case _                        => throw UnsupportedError(s"This column isn't supported: ${column.getClass.getName}")
    }

    (column.isNullable, column) match {
      case (false, _)              => dataType
      case (true, _: ArrayColumn)  => dataType
      case (true, _: NestedColumn) => dataType
      case _                       => s"Nullable($dataType)"
    }
  }

  private def getDefaultExpr(column: Column): String = {
    val defaultExpression =
      if (column.defaultExpression != null) column.defaultExpression.map(_.buildExpression()) else None

    defaultExpression match {
      case Some(expr) => expr
      case None       => defaultValueAsExpr(column)
    }
  }

  private def defaultValueAsExpr(column: Column): String = {
    column match {
      case column: BoolColumn  => column.defaultValue.map(x => s"DEFAULT ${if (x) 1 else 0}").getOrElse("")
      case column: Int8Column  => column.defaultValue.map(x => s"DEFAULT $x").getOrElse("")
      case column: Int16Column => column.defaultValue.map(x => s"DEFAULT $x").getOrElse("")
      case column: Int32Column => column.defaultValue.map(x => s"DEFAULT $x").getOrElse("")
      case column: Int64Column =>
        if (column.defaultValue.isDefined) {
          s"DEFAULT ${column.defaultValue.get}"
        } else {
          ""
        }
      case column: UInt8Column  => column.defaultValue.map(x => s"DEFAULT $x").getOrElse("")
      case column: UInt16Column => column.defaultValue.map(x => s"DEFAULT $x").getOrElse("")
      case column: UInt32Column => column.defaultValue.map(x => s"DEFAULT $x").getOrElse("")
      case column: UInt64Column =>
        if (column.defaultValue.isDefined) {
          s"DEFAULT ${column.defaultValue.get}"
        } else {
          ""
        }
      case column: FloatColumn =>
        if (column.defaultValue.isDefined) {
          s"DEFAULT ${column.defaultValue.get}"
        } else {
          ""
        }
      case column: DoubleColumn =>
        if (column.defaultValue.isDefined) {
          s"DEFAULT ${column.defaultValue.get}"
        } else {
          ""
        }
      case column: StringColumn => column.defaultValue.map(x => s"DEFAULT '$x'").getOrElse("")
      case column: DateColumn =>
        column.defaultValue.map(time => s"DEFAULT '${TimeUtils.format(time, "yyyy-MM-dd")}'").getOrElse("")
      case column: DateTimeColumn =>
        column.defaultValue.map(time => s"DEFAULT '${TimeUtils.format(time, "yyyy-MM-dd")}'").getOrElse("")
      case column: DateTime64Column =>
        column.defaultValue.map(time => s"DEFAULT '${TimeUtils.format(time, "yyyy-MM-dd HH:mm:ss")}'").getOrElse("")
      case _: ArrayColumn => column.defaultExpr.map(expr => s"DEFAULT $expr").getOrElse("")
      case _              => ""
    }
  }

  private def getPrimaryKeyDDL(tableSchema: TableSchema): String = {
    if (tableSchema.primaryKeys != null && tableSchema.primaryKeys.nonEmpty) {
      s"PRIMARY KEY (${tableSchema.primaryKeys.mkString(", ")})"
    } else {
      ""
    }
  }

  private def getPartitionByDDL(tableSchema: TableSchema): String = {
    if (tableSchema.partitionBy != null && tableSchema.partitionBy.nonEmpty) {
      s"PARTITION BY (${tableSchema.partitionBy.mkString(",")})"
    } else {
      ""
    }
  }

  private def getOrderByDDL(tableSchema: TableSchema): String = {
    if (tableSchema.orderBys != null && tableSchema.orderBys.nonEmpty) {
      s"(${tableSchema.orderBys.mkString(", ")})"
    } else {
      "tuple()"
    }
  }

  private def getEngineDDL(tableSchema: TableSchema): String = {
    if (tableSchema.engine.isDefined) {
      tableSchema.engine.get
    } else {
      "MergeTree()"
    }
  }

  override def toUpdateColumnDLL(dbName: String, tblName: String, column: Column, clusterName: String): String = {
    s"""
       |ALTER TABLE `$dbName`.`$tblName` ON CLUSTER $clusterName MODIFY COLUMN IF EXISTS ${toColumnDDLExpr(column)}
       |""".stripMargin
  }

  override def toDeleteColumnDDL(dbName: String, tblName: String, columnName: String, clusterName: String): String = {
    s"""
       |ALTER TABLE $dbName.$tblName ON CLUSTER $clusterName DROP COLUMN IF EXISTS $columnName
       |""".stripMargin
  }
}
