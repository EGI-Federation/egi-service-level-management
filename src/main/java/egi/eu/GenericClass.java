package egi.eu;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;


/***
 * Helper class to get the class name used as generic parameter
 * See also http://gafter.blogspot.com/2006/11/reified-generics-for-java.html
 */
public abstract class GenericClass<G> {

	private final TypeToken<G> typeToken = new TypeToken<G>(getClass()) { };
	private final Type type = typeToken.getType(); // or getRawType() to return Class<? super G>

	public Type getType() {
		return type;
	}
}