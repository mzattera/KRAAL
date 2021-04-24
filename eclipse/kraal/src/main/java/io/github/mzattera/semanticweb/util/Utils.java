/**
 * 
 */
package io.github.mzattera.semanticweb.util;

import org.eclipse.rdf4j.model.Value;

/**
 * Just mixed utilities
 * @author Massimiliano_Zattera
 *
 */
public final class Utils {

	public static String toString(Value v) {
		return escape(v.stringValue());
	}

	public static String escape(String s) {
		return s.replace('"', '\'');
	}
}
