// This file was generated by Mendix Studio Pro.
//
// WARNING: Only the following code will be retained when actions are regenerated:
// - the import list
// - the code between BEGIN USER CODE and END USER CODE
// - the code between BEGIN EXTRA CODE and END EXTRA CODE
// Other code you write will be lost the next time you deploy the project.
// Special characters, e.g., é, ö, à, etc. are supported in comments.

package oql.actions;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.mendix.core.Core;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.connectionbus.data.IDataColumnSchema;
import com.mendix.systemwideinterfaces.connectionbus.data.IDataRow;
import com.mendix.systemwideinterfaces.connectionbus.data.IDataTable;
import com.mendix.systemwideinterfaces.connectionbus.data.IDataTableSchema;
import com.mendix.systemwideinterfaces.connectionbus.requests.IParameterMap;
import com.mendix.systemwideinterfaces.connectionbus.requests.IRetrievalSchema;
import com.mendix.systemwideinterfaces.connectionbus.requests.types.IOQLTextGetRequest;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixIdentifier;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.systemwideinterfaces.core.meta.IMetaAssociation;
import oql.implementation.OQL;
import com.mendix.systemwideinterfaces.core.UserAction;

/**
 * This action executes a given OQL statement and accepts parameters.
 * One can specify using the preserveParameters argument if parameters should be reset after invocation of this action.
 * 
 * Statements can easily be developed using the DataSet and one can use the Mendix documentation (https://docs.mendix.com/refguide7/oql) as reference.
 * 
 * For each column, the action expects an attribute in the result entity with the same name.
 * If the result is the ID of an object, it expects an association with the same name (without the module prefix).
 * 
 * Parameters given should be a list of OQL.Parameter, having at least the ParameterName and ParameterType set.
 * ParameterNames follow the syntax $Name
 * 
 * Example query (taken from OQL.IVK_PerformTests):
 * 
 * SELECT 
 * 	P.id ExamplePersonResult_ExamplePerson,
 * 	P.Name Name,
 * 	P.Number Number,
 * 	P.DateOfBirth DateOfBirth,
 * 	P.Age Age,
 * 	P.LongAge LongAge,
 * 	P.HeightInFloat HeightInFloat,
 * 	P.HeightInDecimal HeightInDecimal,
 * 	P.Active Active,
 * 	P.Gender Gender
 * FROM
 * 	OQL.ExamplePerson P
 * WHERE
 * 	P.Active = $Active AND
 * 	P.Age = $Age AND
 * 	P.DateOfBirth = $DateOfBirth AND
 * 	P.Gender = $Gender AND
 * 	P.HeightInDecimal = $HeightInDecimal AND
 * 	P.HeightInFloat = $HeightInFloat AND
 * 	P.LongAge = $LongAge AND
 * 	P.Name = $Name AND
 * 	P.Number = $Number
 * 
 */
public class ExecuteOQLStatement extends UserAction<java.util.List<IMendixObject>>
{
	private final java.lang.String statement;
	private final java.lang.String returnEntity;
	private final java.lang.Long amount;
	private final java.lang.Long offset;
	private final java.lang.Boolean preserveParameters;

	public ExecuteOQLStatement(
		IContext context,
		java.lang.String _statement,
		java.lang.String _returnEntity,
		java.lang.Long _amount,
		java.lang.Long _offset,
		java.lang.Boolean _preserveParameters
	)
	{
		super(context);
		this.statement = _statement;
		this.returnEntity = _returnEntity;
		this.amount = _amount;
		this.offset = _offset;
		this.preserveParameters = _preserveParameters;
	}

	@java.lang.Override
	public java.util.List<IMendixObject> executeAction() throws Exception
	{
		// BEGIN USER CODE
		IContext context = getContext().createSudoClone();
		ILogNode logger = Core.getLogger(this.getClass().getSimpleName());
		
		logger.debug("Mapping parameters.");
		Map<String, Object> parameters = OQL.getNextParameters();
		List<IMendixObject> result = 
				OQL.executeOQL(context, statement, returnEntity, amount, offset, parameters);	
		
		if (!this.preserveParameters) 
			OQL.resetParameters();
		
		return result;
		// END USER CODE
	}

	/**
	 * Returns a string representation of this action
	 * @return a string representation of this action
	 */
	@java.lang.Override
	public java.lang.String toString()
	{
		return "ExecuteOQLStatement";
	}

	// BEGIN EXTRA CODE
	// END EXTRA CODE
}
