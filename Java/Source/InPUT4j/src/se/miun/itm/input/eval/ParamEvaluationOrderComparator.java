/*-- $Copyright (C) 2012 Felix Dobslaw$


Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished
to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package se.miun.itm.input.eval;

import java.util.Comparator;

import se.miun.itm.input.model.param.Param;
import se.miun.itm.input.util.Q;

/**
 * ParamEvaluationOrder  helps defining the order for the design space parameter tree, depending on the dependencies.
 * @author Felix Dobslaw
 *
 * @param <Element>
 */
public class ParamEvaluationOrderComparator<Element> implements Comparator<Object> {

	/**
	 * initialize the dependencies for param1 and param2
	 * @param param1
	 * @param param2
	 */
	public static void init(Param param1, Param param2) {
		if (!initDependencies(param1, param2))
			initDependencies(param2, param1);
	}

	/**
	 * initialize the dependencies, in case they exist.
	 * @param param1
	 * @param param2
	 * @return
	 */
	private static boolean initDependencies(Param param1,
			Param param2) {
		boolean result = false;
		if (relativeTo(param1, param2, Q.INCL_MAX)
				|| relativeTo(param1, param2, Q.EXCL_MAX)) {
			param1.addMaxDependency(param2);
			param2.addDependee(param1);
			result = true;
		}

		if (relativeTo(param1, param2, Q.INCL_MIN)
				|| relativeTo(param1, param2, Q.EXCL_MIN)) {
			param1.addMinDependency(param2);
			param2.addDependee(param1);
			result = true;
		}
		return result;
	}

	@Override
	public int compare(Object arg0, Object arg1) {
		int result = 0;

		boolean directDependency = false;

		Param param1 = (Param) arg0;
		Param param2 = (Param) arg1;
		if (dependsOn(param1, param2)) {
			result = 1;
			directDependency = true;
		} else if (dependsOn(param2, param1)) {
			result = -1;
			directDependency = true;
		} else

		if (!directDependency) {
			int dep1 = param1.getAmountDirectDependencies();
			int dep2 = param2.getAmountDirectDependencies();
			if (dep1 > dep2)
				result = 1;
			else if (dep1 > dep2)
				result = -1;
			else {
				dep1 = param1.getAmountDependees();
				dep2 = param2.getAmountDependees();
				if (dep1 > dep2)
					result = -1;
				else if (dep1 > dep2)
					result = 1;
				else {
					if (!(arg0 instanceof Param || arg1 instanceof Param))
						result = ((org.jdom2.Element) arg0).getAttributeValue(
								Q.ID_ATTR).compareTo(
								((org.jdom2.Element) arg1)
										.getAttributeValue(Q.ID_ATTR));
					else {
						if (arg0 instanceof Param)
							result = -1;
						else
							result = 1;
					}
				}
			}
		}
		return result;
	}

	/**
	 * a parameter depends on another, if it transitively or directly depends on it, with respect to the min-max definitions.
	 * @param param1
	 * @param param2
	 * @return
	 */
	private boolean dependsOn(Param param1, Param param2) {
		if (param1.dependsOn(param2))
			return true;
		return false;
	}

	private static boolean relativeTo(Param param1, Param param2,
			String extremeAttr) {
		String extremeValue = param1.getAttributeValue(extremeAttr);
		if (extremeValue != null)
			// if the expression contains the second parameters id AND is a leaf
			// : its a relation!
			if (extremeValue.contains(param2.getId())
					&& param2.getChildren().size() == 0)
				return true;
		return false;
	}
}