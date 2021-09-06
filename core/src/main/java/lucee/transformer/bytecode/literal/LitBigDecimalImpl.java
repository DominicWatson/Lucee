package lucee.transformer.bytecode.literal;

import java.math.BigDecimal;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import lucee.print;
import lucee.runtime.op.Caster;
import lucee.runtime.type.LiteralValue;
import lucee.transformer.Factory;
import lucee.transformer.Position;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.bytecode.expression.ExpressionBase;
import lucee.transformer.bytecode.util.Types;
import lucee.transformer.expression.ExprNumber;
import lucee.transformer.expression.literal.LitBigDecimal;

/**
 * A Literal String
 */
public class LitBigDecimalImpl extends ExpressionBase implements LitBigDecimal, ExprNumber {

	private static final Type LITERAL_VALUE = Type.getType(LiteralValue.class);

	private static final Method CONSTR_STRING = new Method("<init>", Types.VOID, new Type[] { Types.STRING });
	private static final Method VALUE_OF = new Method("valueOf", Types.BIG_DECIMAL, new Type[] { Types.LONG_VALUE });
	private static final Method TO_NUMBER_LONG_VALUE = new Method("toNumber", Types.NUMBER, new Type[] { Types.PAGE_CONTEXT, Types.LONG_VALUE });
	private static final Method TO_NUMBER_STRING = new Method("toNumber", Types.NUMBER, new Type[] { Types.PAGE_CONTEXT, Types.STRING });

	private String number;
	private BigDecimal bd;

	public LitBigDecimalImpl(Factory f, String number, Position start, Position end) {
		super(f, start, end);
		this.number = number;

	}

	public LitBigDecimalImpl(Factory f, BigDecimal bd, Position start, Position end) {
		super(f, start, end);
		this.bd = bd;
		this.number = bd.toPlainString();

	}

	@Override
	public Number getNumber() {
		return getBigDecimal();
	}

	@Override
	public Number getNumber(Number defaultValue) {
		return getBigDecimal();
	}

	@Override
	public String getString() {
		return number;
	}

	@Override
	public Boolean getBoolean(Boolean defaultValue) {
		return getBigDecimal().compareTo(BigDecimal.ZERO) != 0;
	}

	@Override
	public BigDecimal getBigDecimal() {
		if (bd == null) bd = new BigDecimal(number);
		return bd;
	}

	@Override
	public Type _writeOut(BytecodeContext bc, int mode) throws TransformerException {
		GeneratorAdapter adapter = bc.getAdapter();

		Long l = justNumberDigits(number) ? Caster.toLong(number, null) : null;
		if (l != null) {
			adapter.loadArg(0);
			adapter.push(l.longValue());
			adapter.invokeStatic(LITERAL_VALUE, TO_NUMBER_LONG_VALUE);

			/*
			 * if (l.longValue() == 0L) adapter.getStatic(Types.BIG_DECIMAL, "ZERO", Types.BIG_DECIMAL); else if
			 * (l.longValue() == 1L) adapter.getStatic(Types.BIG_DECIMAL, "ONE", Types.BIG_DECIMAL); else if
			 * (l.longValue() == 10L) adapter.getStatic(Types.BIG_DECIMAL, "TEN", Types.BIG_DECIMAL); else {
			 * adapter.push(l.longValue()); adapter.invokeStatic(Types.BIG_DECIMAL, VALUE_OF); }
			 */
		}
		else {
			adapter.loadArg(0);
			adapter.push(number);
			adapter.invokeStatic(LITERAL_VALUE, TO_NUMBER_STRING);
			/*
			 * adapter.newInstance(Types.BIG_DECIMAL); adapter.dup(); adapter.push(number);
			 * adapter.invokeConstructor(Types.BIG_DECIMAL, CONSTR_STRING);
			 */
		}

		// adapter.invokeStatic(Types.CASTER, Methods.METHOD_TO_BIG_DECIMAL_STR); // TODOX call constructor
		// directly
		return Types.NUMBER;
	}

	private static boolean justNumberDigits(String number) {
		for (char c: number.toCharArray()) {
			if (c >= '0' && c <= '9') continue;
			return false;
		}

		return true;
	}

	public static void main(String[] args) {
		print.e(justNumberDigits("12344"));
		print.e(justNumberDigits("1234.4"));
	}

}