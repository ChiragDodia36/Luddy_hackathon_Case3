package com.luddy.bloomington_transit.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.luddy.bloomington_transit.data.local.dao.RouteDao;
import com.luddy.bloomington_transit.data.local.dao.RouteDao_Impl;
import com.luddy.bloomington_transit.data.local.dao.ShapeDao;
import com.luddy.bloomington_transit.data.local.dao.ShapeDao_Impl;
import com.luddy.bloomington_transit.data.local.dao.StopDao;
import com.luddy.bloomington_transit.data.local.dao.StopDao_Impl;
import com.luddy.bloomington_transit.data.local.dao.TripDao;
import com.luddy.bloomington_transit.data.local.dao.TripDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile RouteDao _routeDao;

  private volatile StopDao _stopDao;

  private volatile ShapeDao _shapeDao;

  private volatile TripDao _tripDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `routes` (`id` TEXT NOT NULL, `shortName` TEXT NOT NULL, `longName` TEXT NOT NULL, `color` TEXT NOT NULL, `textColor` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `stops` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `code` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `shapes` (`shapeId` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `sequence` INTEGER NOT NULL, PRIMARY KEY(`shapeId`, `sequence`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`tripId` TEXT NOT NULL, `routeId` TEXT NOT NULL, `shapeId` TEXT NOT NULL, `headsign` TEXT NOT NULL, `serviceId` TEXT NOT NULL, PRIMARY KEY(`tripId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `stop_times` (`tripId` TEXT NOT NULL, `stopId` TEXT NOT NULL, `arrivalTime` TEXT NOT NULL, `departureTime` TEXT NOT NULL, `stopSequence` INTEGER NOT NULL, PRIMARY KEY(`tripId`, `stopId`, `stopSequence`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `route_stops` (`routeId` TEXT NOT NULL, `stopId` TEXT NOT NULL, PRIMARY KEY(`routeId`, `stopId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a946fd785a5277b830fd7432fa87cfd8')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `routes`");
        db.execSQL("DROP TABLE IF EXISTS `stops`");
        db.execSQL("DROP TABLE IF EXISTS `shapes`");
        db.execSQL("DROP TABLE IF EXISTS `trips`");
        db.execSQL("DROP TABLE IF EXISTS `stop_times`");
        db.execSQL("DROP TABLE IF EXISTS `route_stops`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsRoutes = new HashMap<String, TableInfo.Column>(5);
        _columnsRoutes.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("shortName", new TableInfo.Column("shortName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("longName", new TableInfo.Column("longName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("color", new TableInfo.Column("color", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("textColor", new TableInfo.Column("textColor", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRoutes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRoutes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRoutes = new TableInfo("routes", _columnsRoutes, _foreignKeysRoutes, _indicesRoutes);
        final TableInfo _existingRoutes = TableInfo.read(db, "routes");
        if (!_infoRoutes.equals(_existingRoutes)) {
          return new RoomOpenHelper.ValidationResult(false, "routes(com.luddy.bloomington_transit.data.local.entity.RouteEntity).\n"
                  + " Expected:\n" + _infoRoutes + "\n"
                  + " Found:\n" + _existingRoutes);
        }
        final HashMap<String, TableInfo.Column> _columnsStops = new HashMap<String, TableInfo.Column>(5);
        _columnsStops.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("lat", new TableInfo.Column("lat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("lon", new TableInfo.Column("lon", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("code", new TableInfo.Column("code", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStops = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStops = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoStops = new TableInfo("stops", _columnsStops, _foreignKeysStops, _indicesStops);
        final TableInfo _existingStops = TableInfo.read(db, "stops");
        if (!_infoStops.equals(_existingStops)) {
          return new RoomOpenHelper.ValidationResult(false, "stops(com.luddy.bloomington_transit.data.local.entity.StopEntity).\n"
                  + " Expected:\n" + _infoStops + "\n"
                  + " Found:\n" + _existingStops);
        }
        final HashMap<String, TableInfo.Column> _columnsShapes = new HashMap<String, TableInfo.Column>(4);
        _columnsShapes.put("shapeId", new TableInfo.Column("shapeId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("lat", new TableInfo.Column("lat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("lon", new TableInfo.Column("lon", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShapes.put("sequence", new TableInfo.Column("sequence", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysShapes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesShapes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoShapes = new TableInfo("shapes", _columnsShapes, _foreignKeysShapes, _indicesShapes);
        final TableInfo _existingShapes = TableInfo.read(db, "shapes");
        if (!_infoShapes.equals(_existingShapes)) {
          return new RoomOpenHelper.ValidationResult(false, "shapes(com.luddy.bloomington_transit.data.local.entity.ShapeEntity).\n"
                  + " Expected:\n" + _infoShapes + "\n"
                  + " Found:\n" + _existingShapes);
        }
        final HashMap<String, TableInfo.Column> _columnsTrips = new HashMap<String, TableInfo.Column>(5);
        _columnsTrips.put("tripId", new TableInfo.Column("tripId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrips.put("routeId", new TableInfo.Column("routeId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrips.put("shapeId", new TableInfo.Column("shapeId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrips.put("headsign", new TableInfo.Column("headsign", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrips.put("serviceId", new TableInfo.Column("serviceId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTrips = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTrips = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTrips = new TableInfo("trips", _columnsTrips, _foreignKeysTrips, _indicesTrips);
        final TableInfo _existingTrips = TableInfo.read(db, "trips");
        if (!_infoTrips.equals(_existingTrips)) {
          return new RoomOpenHelper.ValidationResult(false, "trips(com.luddy.bloomington_transit.data.local.entity.TripEntity).\n"
                  + " Expected:\n" + _infoTrips + "\n"
                  + " Found:\n" + _existingTrips);
        }
        final HashMap<String, TableInfo.Column> _columnsStopTimes = new HashMap<String, TableInfo.Column>(5);
        _columnsStopTimes.put("tripId", new TableInfo.Column("tripId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStopTimes.put("stopId", new TableInfo.Column("stopId", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStopTimes.put("arrivalTime", new TableInfo.Column("arrivalTime", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStopTimes.put("departureTime", new TableInfo.Column("departureTime", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStopTimes.put("stopSequence", new TableInfo.Column("stopSequence", "INTEGER", true, 3, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStopTimes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStopTimes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoStopTimes = new TableInfo("stop_times", _columnsStopTimes, _foreignKeysStopTimes, _indicesStopTimes);
        final TableInfo _existingStopTimes = TableInfo.read(db, "stop_times");
        if (!_infoStopTimes.equals(_existingStopTimes)) {
          return new RoomOpenHelper.ValidationResult(false, "stop_times(com.luddy.bloomington_transit.data.local.entity.StopTimeEntity).\n"
                  + " Expected:\n" + _infoStopTimes + "\n"
                  + " Found:\n" + _existingStopTimes);
        }
        final HashMap<String, TableInfo.Column> _columnsRouteStops = new HashMap<String, TableInfo.Column>(2);
        _columnsRouteStops.put("routeId", new TableInfo.Column("routeId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRouteStops.put("stopId", new TableInfo.Column("stopId", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRouteStops = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRouteStops = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRouteStops = new TableInfo("route_stops", _columnsRouteStops, _foreignKeysRouteStops, _indicesRouteStops);
        final TableInfo _existingRouteStops = TableInfo.read(db, "route_stops");
        if (!_infoRouteStops.equals(_existingRouteStops)) {
          return new RoomOpenHelper.ValidationResult(false, "route_stops(com.luddy.bloomington_transit.data.local.entity.RouteStopEntity).\n"
                  + " Expected:\n" + _infoRouteStops + "\n"
                  + " Found:\n" + _existingRouteStops);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "a946fd785a5277b830fd7432fa87cfd8", "644b6c1cf3fc1903518868e0b4441e78");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "routes","stops","shapes","trips","stop_times","route_stops");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `routes`");
      _db.execSQL("DELETE FROM `stops`");
      _db.execSQL("DELETE FROM `shapes`");
      _db.execSQL("DELETE FROM `trips`");
      _db.execSQL("DELETE FROM `stop_times`");
      _db.execSQL("DELETE FROM `route_stops`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(RouteDao.class, RouteDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(StopDao.class, StopDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ShapeDao.class, ShapeDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TripDao.class, TripDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public RouteDao routeDao() {
    if (_routeDao != null) {
      return _routeDao;
    } else {
      synchronized(this) {
        if(_routeDao == null) {
          _routeDao = new RouteDao_Impl(this);
        }
        return _routeDao;
      }
    }
  }

  @Override
  public StopDao stopDao() {
    if (_stopDao != null) {
      return _stopDao;
    } else {
      synchronized(this) {
        if(_stopDao == null) {
          _stopDao = new StopDao_Impl(this);
        }
        return _stopDao;
      }
    }
  }

  @Override
  public ShapeDao shapeDao() {
    if (_shapeDao != null) {
      return _shapeDao;
    } else {
      synchronized(this) {
        if(_shapeDao == null) {
          _shapeDao = new ShapeDao_Impl(this);
        }
        return _shapeDao;
      }
    }
  }

  @Override
  public TripDao tripDao() {
    if (_tripDao != null) {
      return _tripDao;
    } else {
      synchronized(this) {
        if(_tripDao == null) {
          _tripDao = new TripDao_Impl(this);
        }
        return _tripDao;
      }
    }
  }
}
