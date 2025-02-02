import { SchemaService, SchemaServiceImpl } from '@core/schema/service/SchemaService';
import { DevModule, Di, TestModule } from '@core/common/modules';
import { RandomUtils } from '@/utils';
import { Log } from '@core/utils';
import { SchemaModule } from '@core/schema/module/SchemaModule';
import { HttpTestModule } from '@core/common/modules/TestHttpModule';

describe('Schema service for db listing', () => {
  let schemaService: SchemaService;

  before(() => {
    Di.init([new HttpTestModule(), new DevModule(), new SchemaModule(), new TestModule()]);
    schemaService = Di.get<SchemaService>(SchemaService);
    Log.debug('schemaService is SchemaServiceImpl::', schemaService instanceof SchemaServiceImpl);
  });

  const dbName = `testDb_${RandomUtils.nextInt(0, 15000)}`;
  const createdTblName = 'transaction';
  let createdDbName: string;
  it('should create database successfully', async () => {
    // const request = new DatabaseCreateRequest(dbName, 'Test database');
    // const response = await schemaService.createDatabase(request);
    // expect(response).be.not.null;
    // const db = response as DatabaseInfo;
    // createdDbName = db.name;
  });

  it('should get databaseInfos successfully', async () => {
    // const success = await schemaService.getDatabases();
    // expect(success).is.ok;
  });

  it('should get created db detail successfully', async () => {
    // const response = await schemaService.getDatabaseSchema(createdDbName);
    // expect(response).be.not.null;
    // const dbSchema = response as DatabaseSchema;
    // expect(dbSchema.name).eq(createdDbName);
  });

  it('should create table successfully', async () => {
    // const request = new CreateTableRequest(
    //   createdDbName,
    //   createdTblName,
    //   [
    //     new Int32Column('id', 'Id'),
    //     new DateTimeColumn('created_date', 'Created Date', [], false),
    //     new StringColumn('location', 'Location'),
    //     new StringColumn('shop', 'Shop'),
    //     new StringColumn('sale', 'Sale')
    //   ],
    //   ['id'],
    //   ['id']
    // );
    // const response = await schemaService.createTable(request);
    // expect(response).be.not.null;
    // const tblSchema = response as TableSchema;
    // expect(tblSchema.name).equal(createdTblName);
  });

  it('should delete table successfully', async () => {
    // const success = await schemaService.dropTable(createdDbName, createdTblName);
    // expect(success).is.ok;
  });

  it('should delete database successfully', async () => {
    // const success = await schemaService.dropDatabase(createdDbName);
    // expect(success).is.ok;
  });
});
