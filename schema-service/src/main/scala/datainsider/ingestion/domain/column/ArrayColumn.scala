package datainsider.ingestion.domain

import datainsider.ingestion.domain.column.DefaultExpression

@SerialVersionUID(20200715L)
case class ArrayColumn(
    name: String,
    displayName: String,
    description: Option[String] = None,
    column: Column,
    isNullable: Boolean = false,
    isEncrypted: Boolean = false,
    defaultValue: Option[Seq[_]] = None,
    defaultExpr: Option[String] = None,
    defaultExpression: Option[DefaultExpression] = None
) extends Column {
  override def copyTo(name: String, displayName: String): Column = this.copy(name = name, displayName = displayName)

  override def copyTo(name: String, displayName: String, description: Option[String], isNullable: Boolean, defaultExpression: Option[DefaultExpression], isEncrypted: Boolean): Column = {
    this.copy(name = name, displayName = displayName, description = description, isNullable = isNullable, defaultExpression = defaultExpression, isEncrypted = isEncrypted)
  }
}
