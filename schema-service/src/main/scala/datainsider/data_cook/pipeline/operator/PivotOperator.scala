package datainsider.data_cook.pipeline.operator

import datainsider.client.domain.Implicits.FutureEnhanceLike
import datainsider.client.domain.query.PivotTableSetting
import datainsider.data_cook.domain.operator.TableConfiguration
import datainsider.data_cook.pipeline.exception.{InputInvalid, OperatorException}
import datainsider.data_cook.pipeline.operator.Operator.OperatorId
import datainsider.data_cook.service.table.EtlTableService
import datainsider.ingestion.domain.TableSchema
import datainsider.profiler.Profiler

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

case class PivotOperator(id: OperatorId, query: PivotTableSetting, destTableConfiguration: TableConfiguration)
    extends TableResultOperator

case class PivotOperatorExecutor(tableService: EtlTableService) extends Executor[PivotOperator] {

  @throws[InputInvalid]
  @throws[OperatorException]
  override def process(operator: PivotOperator, context: ExecutorContext): OperatorResult = Profiler(s"[Executor] ${getClass.getSimpleName}.process") {

    ensureInput(operator, context.mapResults)
    try {
      val tableSchema: TableSchema = tableService.createTable(context.orgId, context.jobId, operator.query, operator.destTableConfiguration).syncGet()
      tableService.ingestIfTableEmpty(tableSchema.organizationId, tableSchema.dbName, tableSchema.name, operator.query).syncGet()
      TableResult(operator.id, tableSchema)
    } catch {
      case ex: Throwable => throw new OperatorException(ex.getMessage, ex)
    }

  }

  def ensureInput(operator: PivotOperator, mapResults: mutable.Map[OperatorId, OperatorResult]): Unit = {
    if (operator.parentIds.size != 1) {
      throw InputInvalid("only take one input for pivot operator")
    }

    val parentTable: Option[TableSchema] = mapResults.get(operator.parentIds.head).flatMap(_.getData[TableSchema]())
    if (parentTable.isEmpty) {
      throw InputInvalid("missing input for pivot operator")
    }
  }

}
