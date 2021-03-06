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
package se.miun.itm.input.model.param;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import se.miun.itm.input.model.InPUTException;
import se.miun.itm.input.model.Numeric;
import se.miun.itm.input.model.mapping.IMapping;
import se.miun.itm.input.util.ParamUtil;

/**
 * An internal representation of a constructor, which is a wrapper of the
 * reflect.constructor class, including the information from the InPUT mapping
 * descriptors. It keeps track of its dependencies, and how to get hold of the
 * values that it requires for correct instantiation.
 * 
 * @author Felix Dobslaw
 */
public class InPUTConstructor {

	private static final String[] EMPTY_STRING_ARRAY = new String[0];

	// can be internal identifiers, numeric abbreviations or external classes
	private final String[] formalParamIds;
	//
	private final Set<String> localReferences = new HashSet<String>();
	// //
	private final Set<String> globalReferences = new HashSet<String>();

	private final AStruct param;

	private final ParamStore ps;

	// lazy loading
	private Constructor<?> constructor;

	private boolean init = false;

	private final IMapping mapping;

	public InPUTConstructor(String constrString, AStruct param, ParamStore ps,
			IMapping mapping) throws InPUTException {
		this.ps = ps;
		this.param = param;
		this.mapping = mapping;
		this.formalParamIds = initParams(constrString);
	}

	private String[] initParams(String constrString) throws InPUTException {
		String[] formalParamIds;
		if (constrString != null) {
			formalParamIds = constrString.split(Pattern.quote(" "));
		} else {
			if (param instanceof SChoice) {
				formalParamIds = initParamsByParent();
			} else {
				formalParamIds = EMPTY_STRING_ARRAY;
			}
		}
		return formalParamIds;
	}

	private Class<?>[] initConstructorFormalParamClasses()
			throws InPUTException {
		initContext();
		Class<?>[] formalParams = constructorGuessing();

		if (formalParams == null)
			formalParams = extractParameterClasses();

		return formalParams;
	}

	private void initContext() {
		for (String paramId : formalParamIds)
			if (ps.containsParam(paramId))
				globalReferences.add(paramId);

		for (String paramId : formalParamIds) {
			if (!globalReferences.contains(paramId)) {
				Param localParam = ParamUtil.getParamForLocalId(paramId, param,
						ps);
				if (localParam != null)
					localReferences.add(paramId);
			}
		}
	}

	private Class<?>[] constructorGuessing() throws InPUTException {
		Class<?>[] formalParams = null;
		Constructor<?>[] constructors;
		String component = null;
		try {
			component = mapping.getComponentId();
			constructors = Class.forName(component).getConstructors();
		} catch (Exception e) {
			throw new InPUTException(
					"A class by name "
							+ component
							+ "does not exist in your classpath, but it has been defined as a mapping of parameter "
							+ mapping.getId(), e);
		}

		// first guess: only one constructor?
		if (constructors.length == 1)
			constructor = constructors[0];
		else
			// otherwise, single constructor with as many arguments as
			// formalaramids?
			constructor = getSingleConstructorByContext(constructors);

		if (constructor != null) {
			init = true;
			formalParams = constructor.getParameterTypes();
		}

		return formalParams;
	}

	private Constructor<?> getSingleConstructorByContext(
			Constructor<?>[] constructors) throws InPUTException {
		Constructor<?> constructor;
		Constructor<?>[] numberConstructors = getCostructorsByNumber(constructors);
		if (numberConstructors.length == 1) {
			constructor = numberConstructors[0];
		} else {
			constructor = getSingleCostructorByIoC(numberConstructors);
		}
		return constructor;
	}

	private Constructor<?> getSingleCostructorByIoC(
			Constructor<?>[] constructors) throws InPUTException {
		Class<?>[] context = gatherAvailableConstructorContext();
		Constructor<?> constructor = null;
		List<Constructor<?>> contextConstructors = null;
		if (nonEmptyContext(context)) {
			contextConstructors = retrieveContextConstructors(context,
					constructors);
		}

		if (contextConstructors != null && contextConstructors.size() == 1)
			constructor = contextConstructors.get(0);
		return constructor;
	}

	private List<Constructor<?>> retrieveContextConstructors(
			Class<?>[] context, Constructor<?>[] constructors) {
		LinkedList<Constructor<?>> contextConstructors = new LinkedList<Constructor<?>>(
				Arrays.asList(constructors));
		for (int i = 0; i < context.length; i++) {
			if (context[i] == null)
				continue;
			if (contextConstructors.size() == 1)
				break;

			reduceByContext(contextConstructors, i, context[i]);
		}
		return contextConstructors;
	}

	private void reduceByContext(
			LinkedList<Constructor<?>> contextConstructors, int i,
			Class<?> context) {
		List<Integer> toRemove = new ArrayList<Integer>();
		for (int j = 0; j < contextConstructors.size(); j++)
			if (!contextConstructors.get(j).getParameterTypes()[i]
					.equals(context))
				toRemove.add(j);

		int index;
		for (int j = toRemove.size() - 1; j >= 0; j--) {
			index = toRemove.get(j);
			contextConstructors.remove(index);
		}
	}

	private boolean nonEmptyContext(Class<?>[] context) {
		for (Class<?> cLass : context)
			if (cLass != null)
				return true;
		return false;
	}

	private Class<?>[] gatherAvailableConstructorContext()
			throws InPUTException {
		String identifier;
		Class<?>[] context = new Class<?>[formalParamIds.length];
		Class<?> cLass;
		for (int i = 0; i < context.length; i++) {
			cLass = null;
			// context only reachable if its a global or local id from this
			// paramstore or if it is a direct class
			identifier = formalParamIds[i];
			if (localReferences.contains(identifier))
				cLass = getClassForLocalContext(identifier);
			context[i] = cLass;
		}
		return context;
	}

	private Constructor<?>[] getCostructorsByNumber(
			Constructor<?>[] constructors) {
		List<Constructor<?>> result = new ArrayList<Constructor<?>>();
		int number = formalParamIds.length;
		for (int i = 0; i < constructors.length; i++)
			if (constructors[i].getParameterTypes().length == number)
				result.add(constructors[i]);
		return result.toArray(new Constructor<?>[] {});
	}

	private Class<?>[] extractParameterClasses() throws InPUTException {
		Class<?>[] cLasses;
		if (formalParamIds.length == 0)
			cLasses = AStruct.EMPTY_CLASS_ARRAY;
		else
			cLasses = initParameterClasses();

		return cLasses;
	}

	private Class<?>[] initParameterClasses() throws InPUTException {
		Class<?>[] cLasses;
		cLasses = new Class<?>[formalParamIds.length];
		for (int i = 0; i < cLasses.length; i++)
			cLasses[i] = initClass(formalParamIds[i]);
		return cLasses;
	}

	private String[] initParamsByParent() throws InPUTException {
		InPUTConstructor parentConstructor = ((SParam) param.getParentElement())
				.getInPUTConstructor();
		;
		return parentConstructor.getFormalParamIds();
	}

	// an identifier can be : 1) a class (e.g. java.lang.String)
	// 2) a localId (e.g. someParam) 3) a global id (e.g. representation or
	// selection.size) 4) a numeric enum entry (e.g. integer).
	private Class<?> initClass(String identifier) throws InPUTException {
		Class<?> cLass = null;

		try {
			if (ParamUtil.hasGlobalParamSyntax(identifier)) {
				cLass = getClassForGlobalContext(identifier);
				if (cLass == null)
					cLass = Class.forName(identifier);
			} else
				cLass = getClassForLocalContext(identifier);
		} catch (Exception e) {
		} finally {
			if (cLass == null) {
				throw new InPUTException(param.getId()
						+ ": There is no class, subparam or parameter '"
						+ param.getId() + "' with identifier '" + identifier
						+ "'.");
			}
		}

		return cLass;
	}

	private Class<?> getClassForLocalContext(String identifier)
			throws InPUTException {
		Class<?> cLass = getClassForNumericParam(identifier);

		if (cLass == null) {
			// first try the
			cLass = getClassForGlobalContext(identifier);
			if (cLass == null) {
				Param paramForId = ParamUtil.getParamForLocalId(identifier,
						param, ps);
				if (paramForId != null) {
					cLass = getClassForLocalParam(identifier, paramForId);
				}
			}
		}
		return cLass;
	}

	private Class<?> getClassForLocalParam(String identifier, Param param) {
		Class<?> cLass = null;
		if (param != null)
			cLass = param.getInPUTClass();
		return cLass;
	}

	private Class<?> getClassForNumericParam(String identifier) {
		if (!Numeric.isNumeric(identifier))
			return null;

		return Numeric.valueOf(identifier.toUpperCase()).getPrimitiveClass();
	}

	private Class<?> getClassForGlobalContext(String identifier)
			throws InPUTException {
		Class<?> cLass = null;
		Param param = ps.getParam(identifier);
		if (param != null)
			cLass = param.getInPUTClass();
		else if (ParamUtil.isMethodContext(identifier))
			cLass = ParamUtil.getClassFromMethodReturnType(identifier,
					this.param, ps);

		return cLass;
	}

	private void init() throws InPUTException {
		if (!param.isEnum())
			initClassType();
		init = true;
	}

	private void initClassType() throws InPUTException {
		String className = mapping.getComponentId();
		if (className == null)
			throw new InPUTException(
					param.getId()
							+ ": No entry for this choice element could be found in the code mappings file. Make sure that the mapping reference in your space descriptor points to the correct mapping file.");

		Class<?> cLass;
		try {
			cLass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new InPUTException(param.getId()
					+ ": There is no such class '" + mapping.getComponentId()
					+ "'.", e);
		}
		try {
			Class<?>[] params = initConstructorFormalParamClasses();

			// first check if there is a constructor at all!
			if (cLass.getConstructors().length == 0)
				constructor = null;
			else
				constructor = cLass.getConstructor(params);
		} catch (NoSuchMethodException e) {
			throw new InPUTException(param.getId()
					+ ": There is no such constructor.", e);
		} catch (SecurityException e) {
			throw new InPUTException(param.getId()
					+ ": You do not have the right to invoke the constructor.",
					e);
		}
	}

	public boolean isGlobalIdUsedInConstructor(String paramId)
			throws InPUTException {
		if (!init)
			init();
		return globalReferences.contains(paramId);
	}

	public String[] getFormalParamIds() throws InPUTException {
		return formalParamIds;
	}

	public boolean isLocalInitByConstructor(String localId)
			throws InPUTException {
		if (!init)
			init();
		return localReferences.contains(localId);
	}

	public Object newInstance(Object[] actualParams) throws InPUTException {
		if (!init)
			init();
		try {
			return constructor.newInstance(actualParams);
		} catch (InstantiationException e) {
			throw new InPUTException(
					param.getId()
							+ ": The object could not be instantiated due to some reason.",
					e);
		} catch (IllegalAccessException e) {
			throw new InPUTException(param.getId()
					+ ": The constructor you declared is not visible.", e);
		} catch (IllegalArgumentException e) {
			throw new InPUTException(param.getId()
					+ ": There is no constructor of type '"
					+ constructor.getDeclaringClass().getName()
					+ "' with argumens of type "
					+ getClassesForArguments(actualParams), e);
		} catch (InvocationTargetException e) {
			throw new InPUTException(
					param.getId()
							+ ": Something went wrong with the creation of the constructor.",
					e);
		}
	}

	private String getClassesForArguments(Object[] actualParams) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < actualParams.length; i++) {
			b.append(" ");
			if (actualParams[i] != null) {
				b.append(actualParams[i].getClass().getName());
			} else
				b.append("null");
		}
		return b.toString();
	}

}