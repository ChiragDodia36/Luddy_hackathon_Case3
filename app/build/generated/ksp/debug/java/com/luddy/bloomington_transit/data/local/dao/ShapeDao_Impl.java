package com.luddy.bloomington_transit.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.luddy.bloomington_transit.data.local.entity.ShapeEntity;
import java.lang.Class;
import java.lang.Exception;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ShapeDao_Impl implements ShapeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ShapeEntity> __insertionAdapterOfShapeEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public ShapeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfShapeEntity = new EntityInsertionAdapter<ShapeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `shapes` (`shapeId`,`lat`,`lon`,`sequence`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ShapeEntity entity) {
        statement.bindString(1, entity.getShapeId());
        statement.bindDouble(2, entity.getLat());
        statement.bindDouble(3, entity.getLon());
        statement.bindLong(4, entity.getSequence());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM shapes";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<ShapeEntity> shapes,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfShapeEntity.insert(shapes);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ShapeEntity>> getShapesForRoute(final String routeId) {
    final String _sql = "\n"
            + "        SELECT DISTINCT sh.shapeId, sh.lat, sh.lon, sh.sequence\n"
            + "        FROM shapes sh\n"
            + "        INNER JOIN trips t ON t.shapeId = sh.shapeId\n"
            + "        WHERE t.routeId = ?\n"
            + "        ORDER BY sh.shapeId ASC, sh.sequence ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, routeId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"shapes",
        "trips"}, new Callable<List<ShapeEntity>>() {
      @Override
      @NonNull
      public List<ShapeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfShapeId = 0;
          final int _cursorIndexOfLat = 1;
          final int _cursorIndexOfLon = 2;
          final int _cursorIndexOfSequence = 3;
          final List<ShapeEntity> _result = new ArrayList<ShapeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShapeEntity _item;
            final String _tmpShapeId;
            _tmpShapeId = _cursor.getString(_cursorIndexOfShapeId);
            final double _tmpLat;
            _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            final double _tmpLon;
            _tmpLon = _cursor.getDouble(_cursorIndexOfLon);
            final int _tmpSequence;
            _tmpSequence = _cursor.getInt(_cursorIndexOfSequence);
            _item = new ShapeEntity(_tmpShapeId,_tmpLat,_tmpLon,_tmpSequence);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
