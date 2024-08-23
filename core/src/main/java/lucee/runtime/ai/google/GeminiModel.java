package lucee.runtime.ai.google;

import lucee.runtime.ai.AIModelSupport;
import lucee.runtime.exp.PageException;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Struct;
import lucee.runtime.type.util.KeyConstants;

public class GeminiModel extends AIModelSupport {

	public GeminiModel(Struct raw, String charset) throws PageException {
		// TODO make split better
		super(Caster.toString(raw.get(KeyConstants._name)).substring("models/".length()), raw, charset);
	}

	@Override
	public String getLabel() {
		return Caster.toString(raw.get(KeyConstants._displayName, null), null);
	}
}