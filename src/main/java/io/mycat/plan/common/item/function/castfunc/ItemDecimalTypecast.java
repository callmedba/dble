package io.mycat.plan.common.item.function.castfunc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.druid.sql.ast.SQLDataTypeImpl;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLCastExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;

import io.mycat.plan.common.field.Field;
import io.mycat.plan.common.item.FieldTypes;
import io.mycat.plan.common.item.Item;
import io.mycat.plan.common.item.function.ItemFunc;
import io.mycat.plan.common.time.MySQLTime;


public class ItemDecimalTypecast extends ItemFunc {
	BigDecimal decimal_value = null;
	int precision;
	int dec;

	public ItemDecimalTypecast(Item a, int precision, int dec) {
		super(new ArrayList<Item>());
		args.add(a);
		this.precision = precision;
		this.dec = dec;
	}

	@Override
	public final String funcName() {
		return "decimal_typecast";
	}

	@Override
	public void fixLengthAndDec() {
	}

	@Override
	public BigDecimal valReal() {
		BigDecimal tmp = valDecimal();
		if (nullValue)
			return BigDecimal.ZERO;
		return tmp;
	}

	@Override
	public BigInteger valInt() {
		BigDecimal tmp = valDecimal();
		if (nullValue)
			return BigInteger.ZERO;
		return tmp.toBigInteger();
	}

	@Override
	public String valStr() {
		BigDecimal tmp = valDecimal();
		if (nullValue)
			return null;
		return tmp.toString();
	}

	@Override
	public BigDecimal valDecimal() {
		BigDecimal tmp = args.get(0).valDecimal();

		if ((nullValue = args.get(0).nullValue))
			return null;
		BigDecimal dec = tmp.setScale(this.dec, RoundingMode.HALF_UP);
		return dec;
	}

	@Override
	public boolean getDate(MySQLTime ltime, long flags) {
		return getDateFromDecimal(ltime, flags);
	}

	@Override
	public boolean getTime(MySQLTime ltime) {
		return getTimeFromDecimal(ltime);
	}

	@Override
	public FieldTypes fieldType() {
		return FieldTypes.MYSQL_TYPE_DECIMAL;
	}

	@Override
	public ItemResult resultType() {
		return ItemResult.DECIMAL_RESULT;
	}

	@Override
	public SQLExpr toExpression() {
		SQLCastExpr cast = new SQLCastExpr();
		cast.setExpr(args.get(0).toExpression());
		SQLDataTypeImpl dataType = new SQLDataTypeImpl("DECIMAL");
		if (precision >= 0) {
			dataType.addArgument(new SQLIntegerExpr(precision));
		}
		if (dec > 0) {
			dataType.addArgument(new SQLIntegerExpr(dec));
		}
		cast.setDataType(dataType);
		return cast;
	}

	@Override
	protected Item cloneStruct(boolean forCalculate, List<Item> calArgs, boolean isPushDown, List<Field> fields) {
		List<Item> newArgs = null;
		if (!forCalculate) {
			newArgs = cloneStructList(args);
		} else {
			newArgs = calArgs;
		}
		return new ItemDecimalTypecast(newArgs.get(0), precision, dec);
	}

}