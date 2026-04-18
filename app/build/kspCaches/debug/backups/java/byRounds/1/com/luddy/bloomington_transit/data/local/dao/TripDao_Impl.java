package com.luddy.bloomington_transit.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.luddy.bloomington_transit.data.local.entity.RouteStopEntity;
import com.luddy.bloomington_transit.data.local.entity.StopTimeEntity;
import com.luddy.bloomington_transit.data.local.entity.TripEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TripDao_Impl implements TripDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TripEntity> __insertionAdapterOfTripEntity;

  private final EntityInsertionAdapter<StopTimeEntity> __insertionAdapterOfStopTimeEntity;

  private final EntityInsertionAdapter<RouteStopEntity> __insertionAdapterOfRouteStopEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllTrips;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllStopTimes;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllRouteStops;

  public TripDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTripEntity = new EntityInsertionAdapter<TripEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `trips` (`tripId`,`routeId`,`shapeId`,`headsign`,`serviceId`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TripEntity entity) {
        statement.bindString(1, entity.getTripId());
        statement.bindString(2, entity.getRouteId());
        statement.bindString(3, entity.getShapeId());
        statement.bindString(4, entity.getHeadsign());
        statement.bindString(5, entity.getServiceId());
      }
    };
    this.__insertionAdapterOfStopTimeEntity = new EntityInsertionAdapter<StopTimeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `stop_times` (`tripId`,`stopId`,`arrivalTime`,`departureTime`,`stopSequence`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StopTimeEntity entity) {
        statement.bindString(1, entity.getTripId());
        statement.bindString(2, entity.getStopId());
        statement.bindString(3, entity.getArrivalTime());
        statement.bindString(4, entity.getDepartureTime());
        statement.bindLong(5, entity.getStopSequence());
      }
    };
    this.__insertionAdapterOfRouteStopEntity = new EntityInsertionAdapter<RouteStopEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `route_stops` (`routeId`,`stopId`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RouteStopEntity entity) {
        statement.bindString(1, entity.getRouteId());
        statement.bindString(2, entity.getStopId());
      }
    };
    this.__preparedStmtOfDeleteAllTrips = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM trips";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllStopTimes = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM stop_times";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllRouteStops = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM route_stops";
        return _query;
      }
    };
  }

  @Override
  public Object insertTrips(final List<TripEntity> trips,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTripEntity.insert(trips);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertStopTimes(final List<StopTimeEntity> stopTimes,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfStopTimeEntity.insert(stopTimes);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertRouteStops(final List<RouteStopEntity> routeStops,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfRouteStopEntity.insert(routeStops);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllTrips(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllTrips.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllTrips.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllStopTimes(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllStopTimes.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllStopTimes.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllRouteStops(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllRouteStops.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllRouteStops.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getTripById(final String tripId,
      final Continuation<? super TripEntity> $completion) {
    final String _sql = "SELECT * FROM trips WHERE tripId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tripId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TripEntity>() {
      @Override
      @Nullable
      public TripEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTripId = CursorUtil.getColumnIndexOrThrow(_cursor, "tripId");
          final int _cursorIndexOfRouteId = CursorUtil.getColumnIndexOrThrow(_cursor, "routeId");
          final int _cursorIndexOfShapeId = CursorUtil.getColumnIndexOrThrow(_cursor, "shapeId");
          final int _cursorIndexOfHeadsign = CursorUtil.getColumnIndexOrThrow(_cursor, "headsign");
          final int _cursorIndexOfServiceId = CursorUtil.getColumnIndexOrThrow(_cursor, "serviceId");
          final TripEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTripId;
            _tmpTripId = _cursor.getString(_cursorIndexOfTripId);
            final String _tmpRouteId;
            _tmpRouteId = _cursor.getString(_cursorIndexOfRouteId);
            final String _tmpShapeId;
            _tmpShapeId = _cursor.getString(_cursorIndexOfShapeId);
            final String _tmpHeadsign;
            _tmpHeadsign = _cursor.getString(_cursorIndexOfHeadsign);
            final String _tmpServiceId;
            _tmpServiceId = _cursor.getString(_cursorIndexOfServiceId);
            _result = new TripEntity(_tmpTripId,_tmpRouteId,_tmpShapeId,_tmpHeadsign,_tmpServiceId);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTripsForRoute(final String routeId,
      final Continuation<? super List<TripEntity>> $completion) {
    final String _sql = "SELECT * FROM trips WHERE routeId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, routeId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TripEntity>>() {
      @Override
      @NonNull
      public List<TripEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTripId = CursorUtil.getColumnIndexOrThrow(_cursor, "tripId");
          final int _cursorIndexOfRouteId = CursorUtil.getColumnIndexOrThrow(_cursor, "routeId");
          final int _cursorIndexOfShapeId = CursorUtil.getColumnIndexOrThrow(_cursor, "shapeId");
          final int _cursorIndexOfHeadsign = CursorUtil.getColumnIndexOrThrow(_cursor, "headsign");
          final int _cursorIndexOfServiceId = CursorUtil.getColumnIndexOrThrow(_cursor, "serviceId");
          final List<TripEntity> _result = new ArrayList<TripEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TripEntity _item;
            final String _tmpTripId;
            _tmpTripId = _cursor.getString(_cursorIndexOfTripId);
            final String _tmpRouteId;
            _tmpRouteId = _cursor.getString(_cursorIndexOfRouteId);
            final String _tmpShapeId;
            _tmpShapeId = _cursor.getString(_cursorIndexOfShapeId);
            final String _tmpHeadsign;
            _tmpHeadsign = _cursor.getString(_cursorIndexOfHeadsign);
            final String _tmpServiceId;
            _tmpServiceId = _cursor.getString(_cursorIndexOfServiceId);
            _item = new TripEntity(_tmpTripId,_tmpRouteId,_tmpShapeId,_tmpHeadsign,_tmpServiceId);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getStopTimesForStop(final String stopId,
      final Continuation<? super List<StopTimeEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT st.* FROM stop_times st\n"
            + "        INNER JOIN trips t ON t.tripId = st.tripId\n"
            + "        WHERE st.stopId = ?\n"
            + "        ORDER BY st.arrivalTime ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, stopId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<StopTimeEntity>>() {
      @Override
      @NonNull
      public List<StopTimeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTripId = CursorUtil.getColumnIndexOrThrow(_cursor, "tripId");
          final int _cursorIndexOfStopId = CursorUtil.getColumnIndexOrThrow(_cursor, "stopId");
          final int _cursorIndexOfArrivalTime = CursorUtil.getColumnIndexOrThrow(_cursor, "arrivalTime");
          final int _cursorIndexOfDepartureTime = CursorUtil.getColumnIndexOrThrow(_cursor, "departureTime");
          final int _cursorIndexOfStopSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "stopSequence");
          final List<StopTimeEntity> _result = new ArrayList<StopTimeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StopTimeEntity _item;
            final String _tmpTripId;
            _tmpTripId = _cursor.getString(_cursorIndexOfTripId);
            final String _tmpStopId;
            _tmpStopId = _cursor.getString(_cursorIndexOfStopId);
            final String _tmpArrivalTime;
            _tmpArrivalTime = _cursor.getString(_cursorIndexOfArrivalTime);
            final String _tmpDepartureTime;
            _tmpDepartureTime = _cursor.getString(_cursorIndexOfDepartureTime);
            final int _tmpStopSequence;
            _tmpStopSequence = _cursor.getInt(_cursorIndexOfStopSequence);
            _item = new StopTimeEntity(_tmpTripId,_tmpStopId,_tmpArrivalTime,_tmpDepartureTime,_tmpStopSequence);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getStopTimesForTrip(final String tripId,
      final Continuation<? super List<StopTimeEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT st.* FROM stop_times st\n"
            + "        WHERE st.tripId = ?\n"
            + "        ORDER BY st.stopSequence ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tripId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<StopTimeEntity>>() {
      @Override
      @NonNull
      public List<StopTimeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTripId = CursorUtil.getColumnIndexOrThrow(_cursor, "tripId");
          final int _cursorIndexOfStopId = CursorUtil.getColumnIndexOrThrow(_cursor, "stopId");
          final int _cursorIndexOfArrivalTime = CursorUtil.getColumnIndexOrThrow(_cursor, "arrivalTime");
          final int _cursorIndexOfDepartureTime = CursorUtil.getColumnIndexOrThrow(_cursor, "departureTime");
          final int _cursorIndexOfStopSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "stopSequence");
          final List<StopTimeEntity> _result = new ArrayList<StopTimeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StopTimeEntity _item;
            final String _tmpTripId;
            _tmpTripId = _cursor.getString(_cursorIndexOfTripId);
            final String _tmpStopId;
            _tmpStopId = _cursor.getString(_cursorIndexOfStopId);
            final String _tmpArrivalTime;
            _tmpArrivalTime = _cursor.getString(_cursorIndexOfArrivalTime);
            final String _tmpDepartureTime;
            _tmpDepartureTime = _cursor.getString(_cursorIndexOfDepartureTime);
            final int _tmpStopSequence;
            _tmpStopSequence = _cursor.getInt(_cursorIndexOfStopSequence);
            _item = new StopTimeEntity(_tmpTripId,_tmpStopId,_tmpArrivalTime,_tmpDepartureTime,_tmpStopSequence);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM trips";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
