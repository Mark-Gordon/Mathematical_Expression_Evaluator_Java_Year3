import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

class ValueNode {

    String type;
    ArrayList value = new ArrayList();
    ArrayList children = new ArrayList();

    // sets the type for the Node (long, set, pair)
    public ValueNode(String type) {

        this.type = type;

    }

    public void addChild(ArrayList val, HashMap<String, ValueNode> valuesMap) {

        if (type.equals("var")) {

            // adds value to the current node
            value.add((String) val.get(0));

            // gets children nodes and adds them to current node
            ArrayList<ValueNode> list = new ArrayList<ValueNode>();
            children.add(valuesMap.get(val.get(0)));

        }
        // if type is long then this is a leaf node, just set value with no children
        else if (type.equals("long")) {
            value.add((long) val.get(0));

        } else if (type.equals("pair") || type.equals("set")) {

            // gets the datatype the child will be
            String childType = getChildType(val);

            ValueNode child = new ValueNode(childType);

            // Gets the values of the child
            ArrayList childValues = getChildValues(val, childType);

            // will creates the child's children
            child.addChild(childValues, valuesMap);

            // set currents node child
            children.add(child);
            // set current node values
            value.add(childValues);

        }

    }

    // Gets the values associated with the children nodes
    private ArrayList getChildValues(ArrayList val, String childType) {

        ArrayList list = new ArrayList();

        if (childType.equals("long")) {
            list.add((long) val.get(0));
        } else if (childType.equals("var")) {
            list.add((String) val.get(0));
        }

        return list;
    }

    // Gets the data type associated with the children
    private String getChildType(ArrayList val) {

        String childtype = null;

        try {
            long num = (long) val.get(0);
            childtype = "long";
        } catch (Exception isVar) {
            childtype = "var";
        }

        return childtype;
    }

    public String getType() {
        return type;
    }

    public ArrayList getChildren() {
        return children;
    }

    public ArrayList getValue() {
        return value;
    }

    public static void printToFile(ValueNode node, PrintWriter writer, String var) {

        // handles the print out of the output
        writer.print("which is ");

        // String is used for final output, I tried to prevent this and had it working
        // without it, but it made equality and member checks significantly harder
        String finalString = "";
        finalString = getPrintString(node);

        // writes final output values to file
        writer.println(finalString);

    }

    // gets the string for the node
    public static String getPrintString(ValueNode node) {

        String finalString = "";

        ArrayList<ValueNode> values = new ArrayList<ValueNode>();

        // gets the nodes children
        values.addAll(node.getChildren());

        if (node.type.equals("var")) {

            try {
                // Gets the children node of variable
                ValueNode varNode = (ValueNode) node.getChildren().get(0);

                // adds result to string
                finalString += getPrintString(varNode);
            } catch (Exception empty) {
                // if empty
                return finalString += "{ }";
            }

        }

        if (node.type.equals("long")) {
            // writer.print(node);
            finalString += node.getValue().get(0);
            return finalString;
        }

        if (node.type.equals("pair") || node.type.equals("set")) {

            if (node.type.equals("pair")) {
                finalString += "(";
            } else if (node.type.equals("set")) {
                finalString += "{";
            }

            if (values.size() > 0) {

                for (int i = 0; i < values.size() - 1; i++) {

                    finalString += getPrintString(values.get(i));
                    finalString += ", ";
                }
                // handles last value as last value does not contain comma
                finalString += getPrintString(values.get(values.size() - 1));

            }
            if (node.type.equals("pair")) {
                finalString += ")";
            } else if (node.type.equals("set")) {
                finalString += "}";
            }
        }
        return finalString;

    }

}

public class program {

    static int varnum = 0;

    // This method will update the hashmap with the new added set, as well as
    // provoke other methods which build up the tree for the set and write to file
    private static HashMap<String, ValueNode> getSetMap(String var, String operator, JSONObject arr,
            JSONObject valueObject, HashMap<String, ValueNode> valueMap, PrintWriter writer) {

        try {

            // Creates top of new tree with its given operator type (pair/set)
            ValueNode rootNode = new ValueNode(operator);

            JSONArray valueArray = (JSONArray) valueObject.get("arguments");

            // treeset used to order variables and remove duplicates
            TreeSet<String> variableSet = new TreeSet<>();
            TreeSet<Long> longset = new TreeSet<>();

            // map used connect the print values of the sets to the JSONObject, allows for
            // duplicates to be removed by the variableset and connect back to their
            // JSONObject values
            HashMap<String, JSONObject> setMap = new HashMap<String, JSONObject>();

            // holds the original values for the "Let x be y" printout
            ArrayList originalValues = new ArrayList();

            // adds each value to their appropriate treeset
            for (int i = 0; i < valueArray.size(); i++) {

                // will try for variables
                try {
                    JSONObject Obj = (JSONObject) valueArray.get(i);
                    String varVal = (String) Obj.get("variable");
                    ValueNode currentNode = valueMap.get(varVal);
                    String val = currentNode.getPrintString(currentNode);
                    originalValues.add(varVal);

                    // if the variable holds a long then add to longset
                    try {
                        long isLong = Long.valueOf(val);
                        longset.add(isLong);

                        // else add to variable set
                    } catch (Exception isVariable) {
                        variableSet.add(val);
                        setMap.put(val, Obj);
                    }
                    // handles case of long value originally
                } catch (Exception handleLong) {
                    originalValues.add(valueArray.get(i).toString());
                    longset.add((Long) valueArray.get(i));

                }
            }

            int longsetSize = longset.size();

            // all values in tree are stored in arrays to prevent complication between
            // single and multi valued nodes
            ArrayList longvalues[] = new ArrayList[longsetSize];

            // adds longvalues to the tree
            for (int i = 0; i < longsetSize; i++) {
                longvalues[i] = new ArrayList();
                long val = longset.pollFirst();

                // checks if any variables in the variableset leads to a long in the longset,
                // removing if so to avoid duplication
                variableSet.remove(String.valueOf(val));

                longvalues[i].add(val);
                // adds children to tree, the tree will then generate for all children below
                // this point
                rootNode.addChild(longvalues[i], valueMap);
            }

            // An arraylist is used to hold the values at each node in the tree
            ArrayList values[] = new ArrayList[valueArray.size()];

            int variableSetCount = variableSet.size();

            // will add the variable values to the tree, this is done second because number
            // values become before pairs/sets in a set
            for (int i = 0; i < variableSetCount; i++) {

                values[i] = new ArrayList();
                // Try for variable value, if fails then must contain a long which is handled in
                // catch

                JSONObject Obj = setMap.get(variableSet.pollFirst());
                String varVal = (String) Obj.get("variable");
                values[i].add(varVal);

                // Add each child value to the rootNode
                rootNode.addChild(values[i], valueMap);
            }

            // handles the write out of "Let x be y"
            printLetSectionSet(var, writer, originalValues);

            // handles the write to file for the new value, starting from the root node
            // traverses tree to get the final value
            rootNode.printToFile(rootNode, writer, var);

            valueMap.put(var, rootNode);

        } catch (Exception invalidSet) {
            writer.println("Let " + var + " be {" + var + "} which has no value");
        }

        return valueMap;
    }

    // handles the write to file for the "let x be y" for sets
    private static void printLetSectionSet(String var, PrintWriter writer, ArrayList originalValues) {

        writer.print("Let " + var + " be {");

        int originalValuesCount = originalValues.size() - 1;

        for (int i = 0; i < originalValuesCount; i++) {
            writer.print(originalValues.get(0) + ", ");
            originalValues.remove(0);
        }

        // This handles the last value so there isn't a comma at end of input
        if (originalValues.size() > 0) {
            writer.print(originalValues.get(0));
        }

        writer.print("}");
    }

    // This method will update the hashmap with the new added pair, as well as
    // provoke other methods which build up the tree for the pair and write to file
    private static HashMap<String, ValueNode> getPairMap(String var, String operator, JSONObject arr,
            JSONObject valueObject, HashMap<String, ValueNode> valueMap, PrintWriter writer) {

        try {

            JSONArray valueArray = (JSONArray) valueObject.get("arguments");

            // Creates top of new tree with its given operator type (pair/set)
            ValueNode rootNode = new ValueNode(operator);

            // An arraylist is used to hold the values at each node in the tree
            ArrayList values[] = new ArrayList[valueArray.size()];

            for (int i = 0; i < valueArray.size(); i++) {

                values[i] = new ArrayList();
                // Try for variable value, if fails then must contain a long which is handled in
                // catch
                try {
                    JSONObject Obj = (JSONObject) valueArray.get(i);
                    String varVal = (String) Obj.get("variable");
                    values[i].add(varVal);
                } catch (Exception handleLong) {
                    values[i].add(valueArray.get(i));
                }
                // Add each child value to the rootNode
                rootNode.addChild(values[i], valueMap);
            }

            writer.print("Let " + var + " be (");
            writer.print(values[0].get(0) + ", " + values[1].get(0) + ")");

            // handles the print, starting from the root node works its way down to get the
            // displayed values
            rootNode.printToFile(rootNode, writer, var);

            valueMap.put(var, rootNode);

        } catch (Exception hasNoValue) {
            writer.println("Let " + var + " be (" + var + ") which has no value");
        }

        return valueMap;
    }

    public static void main(String[] args) {

        // gets current directory and adds the appropriate file to the filepath
        String currentDir = System.getProperty("user.dir");
        String testDir = currentDir + File.separator + "simple-input.json";
        String outputDir = currentDir + File.separator + "output.txt";

        File file = new File(outputDir);

        PrintWriter writer = null;

        try {
            writer = new PrintWriter(file);
        } catch (FileNotFoundException writeFailed) {
            writeFailed.printStackTrace();
            return;
        }

        // Map is used to link variables to their node values which is useful
        // When we need to check if variables have came up before and to use their
        // values
        HashMap<String, ValueNode> valuesMap = new HashMap<String, ValueNode>();

        ValueNode emptySet = new ValueNode("set");
        valuesMap.put("emptySet", emptySet);

        JSONParser parser = new JSONParser();

        try {

            Object obj = parser.parse(new FileReader(testDir));
            JSONObject jsonObject = (JSONObject) obj;

            JSONArray array = (JSONArray) jsonObject.get("declaration-list");

            Iterator<JSONObject> i = array.iterator();

            // will iterate over each object in the declaration list
            while (i.hasNext()) {

                JSONObject currentObj = i.next();
                String var = (String) currentObj.get("declared-variable");

                // will try to parse value as long, if fails it must be a JSONObject which is
                // handled in the catch
                try {
                    // Creates a new tree just to store a single long value
                    ValueNode rootNode = new ValueNode("long");

                    long val = (long) currentObj.get("value");

                    // ValueNode stores items as lists even if they are single values, this prevents
                    // any complication between storing sets, pairs and longs
                    ArrayList<Long> list = new ArrayList<Long>();
                    list.add(val);

                    rootNode.addChild(list, valuesMap);

                    valuesMap.put(var, rootNode);

                    // handles print
                    String inputSetence = "Let " + var + " be " + val;
                    writer.println(inputSetence + " which is " + val);

                } catch (Exception e) {

                    try {

                        JSONObject valueJSON = (JSONObject) currentObj.get("value");

                        String operator = (String) valueJSON.get("operator");

                        if (operator.equals("pair")) {
                            // will handle the output of the pair and return updated map
                            valuesMap = getPairMap(var, operator, currentObj, valueJSON, valuesMap, writer);
                        } else if (operator.equals("set")) {
                            valuesMap = getSetMap(var, operator, currentObj, valueJSON, valuesMap, writer);
                        } else if (operator.equals("equal")) {
                            valuesMap = handleEquality(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("member")) {
                            valuesMap = handleMember(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("is-function")) {
                            valuesMap = isFunction(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("apply-function")) {
                            valuesMap = applyFunction(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("union")) {
                            valuesMap = handleUnion(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("domain")) {
                            valuesMap = handleDomain(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("inverse")) {
                            valuesMap = handleInverse(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("function-space")) {
                            valuesMap = handleFunctionSpace(var, operator, valueJSON, valuesMap, writer);
                        } else if (operator.equals("diagonalize")) {
                            valuesMap = handleDiagonal(var, operator, valueJSON, valuesMap, writer);
                        } else {

                            System.err.println("Didn't recognise operator " + operator);
                        }

                    } catch (Exception hasNoValue) {
                        writer.println("Let " + var + " be " + var + " which has no value");
                    }

                }
            }

        } catch (Exception parseJSONError) {
            System.err.println("Unable to parse JSON File");
        }

        writer.close();
    }

    private static HashMap<String, ValueNode> handleDiagonal(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        ValueNode rootNode = new ValueNode("set");

        ValueNode values[] = new ValueNode[4];

        //used to print out the values input into the diag function 
        String inputValues[] = new String[4];

        try {

            // obtains the values that diag will be done on
            for (int i = 0; i < 4; i++) {

                try {
                    JSONObject variableToSetObject = (JSONObject) valueArray.get(i);
                    String variable = (String) variableToSetObject.get("variable");
                    inputValues[i] = variable;
                    values[i] = valuesMap.get(variable);
                } catch (Exception e) {
                    ValueNode longNode = new ValueNode("long");
                    ArrayList list = new ArrayList();
                    long val = (long) valueArray.get(i);
                    inputValues[i] = Long.toString(val);
                    list.add(val);
                    longNode.addChild(list, valuesMap);
                    values[i] = longNode;
                }

            }
            // rule is used to determine which print out value to use
            int rule = 0;
            // count is used to increment every time a rule is applied, the number of times
            // a rule is applied should be equal to the number of data items in the set V1
            int count[] = new int[3];

            for (int i = 0; i < 3; i++)
                count[i] = 0;

            //V1 has to be of type set
            if (!values[0].type.equals("set"))
                throw new Exception();

            //loops through each individual value in the set V1
            for (int i = 0; i < values[0].getChildren().size(); i++) {

                // gets the value for use in function application
                ValueNode applyValue = (ValueNode) values[0].getChildren().get(i);


                // does the first application of V2(i)(i)
                ValueNode firstApplicationNode = applyFunctionHelper(values[1],
                        applyValue.getPrintString(applyValue));

                // if first application fails then the end value is V4 by the rule  If V2(i)(i)
                // has no value, then F(i) = V4
                if (firstApplicationNode == null) {
                    rule = 0;
                    count[rule]++;
                    continue;
                }

                ValueNode firstApplicationValue = (ValueNode) firstApplicationNode.getChildren().get(0);

                // if first application fails then the end value is V4 by the rule  If V2(i)(i)
                // has no value, then F(i) = V4
                if (firstApplicationValue == null) {
                    rule = 0;
                    count[rule]++;
                    continue;
                }

                // does the second application of V2(i)(i)
                ValueNode secondApplicationValue = applyFunctionHelper(firstApplicationValue,
                        applyValue.getPrintString(applyValue));

                // if second application fails then the end value is V4 by the rule  If
                // V2(i)(i) has no value, then F(i) = V4
                if (secondApplicationValue == null) {
                    rule = 0;
                    count[rule]++;
                    continue;
                }

                // does the final application via the rule - If V3(V2(i)(i)) has a value, then
                // F(i) = V3(V2(i)(i)).
                ValueNode finalNode = applyFunctionHelper(values[2],
                        secondApplicationValue.getPrintString(secondApplicationValue));

                //Design of programs means that when pairs/sets are created they must
                //be given a variable
                varnum = varnum + varnum;
                String pairVar = "pair" + varnum;

                valuesMap = createPair(applyValue, finalNode, valuesMap, pairVar);

                ArrayList value = new ArrayList();
                value.add(pairVar);

                rootNode.addChild(value, valuesMap);

                //if V3(V2(i)(i)) does not have a value then increment for the second rule
                if (finalNode == null) {
                    rule = 1;
                    count[rule]++;
                    continue;
                }
                rule = 2;
                count[rule]++;

            }

            valuesMap = printDiag(count, values, rootNode, values[0].getChildren().size(), var, writer, inputValues,
                    valuesMap);

        } catch (Exception diagFailed) {
            String inputSetence = "Let " + var + " be @diagonalize(" + inputValues[0] + ", " + inputValues[1] + ","
                    + inputValues[2] + ", " + inputValues[3] + ")";
            writer.println(inputSetence + " which has no value");
        }
        return valuesMap;

    }

    private static HashMap<String, ValueNode> printDiag(int[] count, ValueNode[] values, ValueNode rootNode, int size,
            String var, PrintWriter writer, String[] inputValues, HashMap<String, ValueNode> valuesMap) {

        //handles the printing of rule 1  If V2(i)(i) has no value, then F(i) = V4.
        if (size == count[0]) {

            ValueNode set = new ValueNode("set");

            for (int i = 0; i < values[0].getChildren().size(); i++) {

                varnum = varnum + varnum;
                String pairVar = "pair" + varnum;
                valuesMap = createPair((ValueNode) values[0].getChildren().get(i), values[3], valuesMap, pairVar);

                ArrayList list = new ArrayList();
                list.add(pairVar);

                set.addChild(list, valuesMap);

            }

            String inputSetence = "Let " + var + " be @diagonalize(" + inputValues[0] + ", " + inputValues[1] + ","
                    + inputValues[2] + ", " + inputValues[3] + ")";

            writer.println(inputSetence + " which is " + set.getPrintString(set));

            valuesMap.put(var, set);

        } else if (size == count[2]) { //handles the writing of rule 3  If V3(V2(i)(i)) has a value, then F(i) = V3(V2(i)(i)).
            String inputSetence = "Let " + var + " be @diagonalize(" + inputValues[0] + ", " + inputValues[1] + ","
                    + inputValues[2] + ", " + inputValues[3] + ")";
            writer.println(inputSetence + " which is " + rootNode.getPrintString(rootNode));
            valuesMap.put(var, rootNode);
        } else { //handles the writing of rule 2  If V2(i)(i) has a value and V3(V2(i)(i)) has no value, then F(i) has no value.
            String inputSetence = "Let " + var + " be @diagonalize(" + values[0] + ", " + values[1] + "," + values[2]
                    + ", " + values[3] + ")";
            writer.println(inputSetence + " which has no value");
        }

        return valuesMap;

    }

    private static HashMap<String, ValueNode> handleFunctionSpace(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        ValueNode rootNode = new ValueNode("set");

        String[] variable = new String[2];

        ValueNode[] sets = new ValueNode[2];

        ArrayList[] pairChildren = new ArrayList[2];

        ArrayList[] varsToAdd = new ArrayList[2];

        ArrayList VarsToAddToFinalSet = new ArrayList();

        VarsToAddToFinalSet.add("emptySet");

        HashMap<String, String> setMap = new HashMap<String, String>();

        try {

            // loops getting the sets being applied in the function space and their
            // individual values
            for (int i = 0; i < 2; i++) {

                JSONObject variableToSetObject = (JSONObject) valueArray.get(i);

                variable[i] = (String) variableToSetObject.get("variable");

                ValueNode setNode = (ValueNode) valuesMap.get(variable[i]);
                sets[i] = setNode;

                pairChildren[i] = new ArrayList();

                for (int y = 0; y < sets[i].getChildren().size(); y++) {
                    ValueNode num = (ValueNode) sets[i].getChildren().get(y);
                    pairChildren[i].add(num);
                }
            }

            // produces the first set of pairs that will be used to produce all following
            // sets
            // it will make pairs by making combinations from all values in the first set to
            // individual values in the second set
            for (int y = 0; y < pairChildren[0].size(); y++) {

                ValueNode FirstSetChild = (ValueNode) pairChildren[0].get(y);

                varsToAdd[y] = new ArrayList();

                for (int z = 0; z < pairChildren[1].size(); z++) {
                    ValueNode SecondSetChild = (ValueNode) pairChildren[1].get(z);

                    // The way the program is set up, pairs and sets need to be given variables so
                    // this
                    // is how this is achieved when no variable is given
                    varnum = varnum + 1;
                    String pairVar = "addedpair" + varnum;

                    valuesMap = createPair(FirstSetChild, SecondSetChild, valuesMap, pairVar);
                    varsToAdd[y].add(pairVar);

                    // Creates a set of the pair and stores its variable so it can be used in the
                    // final result
                    ValueNode set = new ValueNode("set");

                    ArrayList list = new ArrayList();

                    list.add(pairVar);

                    set.addChild(list, valuesMap);

                    varnum = varnum + 1;
                    String setVar = "addedset" + varnum;

                    valuesMap.put(setVar, set);

                    VarsToAddToFinalSet.add(setVar);
                }
            }

            // loops through the previously created pairs and combines them to create new
            // sets of pairs
            // to be used in the final output
            for (int i = 0; i < varsToAdd[0].size(); i++) {

                ValueNode pair1 = valuesMap.get(varsToAdd[0].get(i));
                ValueNode pair1input = (ValueNode) pair1.getChildren().get(0);

                String pair1value = pair1input.getPrintString(pair1input);

                for (int y = 0; y < varsToAdd[1].size(); y++) {

                    ValueNode pair2 = valuesMap.get(varsToAdd[1].get(y));
                    ValueNode pair2input = (ValueNode) pair2.getChildren().get(0);

                    String pair2value = pair2input.getPrintString(pair2input);

                    // if both input values are equal for both pairs, then a set can't be made for
                    // them as
                    // it would mean the final function would have multiple results for the same
                    // input
                    if (pair1value.equals(pair2value)) {
                        continue;
                    }

                    // Finall we create the set for the paired pairs
                    ValueNode newSet = new ValueNode("set");

                    ArrayList list = new ArrayList();
                    ArrayList list2 = new ArrayList();

                    list.add(varsToAdd[0].get(i));
                    list2.add(varsToAdd[1].get(y));

                    newSet.addChild(list, valuesMap);
                    newSet.addChild(list2, valuesMap);

                    // set is added to the map and the variable is stored so it can be found for
                    // building the final resulting set
                    varnum = varnum + 1;
                    String setVar = "addedpair" + varnum;

                    valuesMap.put(setVar, newSet);

                    VarsToAddToFinalSet.add(setVar);

                }
            }

            // Treeset is used for adding all the values so duplicates are removed and order
            // is achieved
            TreeSet<String> set = new TreeSet<>();

            // Loops through the variables to add, adding their print out values to the
            // treeset and adding their print out
            // values and variables to a map so the ValueNodes can be retrieved again after
            // duplicates haven been removed
            for (int z = 0; z < VarsToAddToFinalSet.size(); z++) {

                ValueNode setToAdd = valuesMap.get(VarsToAddToFinalSet.get(z));
                set.add(setToAdd.getPrintString(setToAdd));

                setMap.put(setToAdd.getPrintString(setToAdd), (String) VarsToAddToFinalSet.get(z));
            }

            // Finally removes from set adding to the final set
            int setSize = set.size();
            for (int i = 0; i < setSize; i++) {
                ArrayList list = new ArrayList();

                list.add(setMap.get(set.pollFirst()));

                rootNode.addChild(list, valuesMap);

            }

            valuesMap.put(var, rootNode);

            // prints out to file
            String inputSetence = "Let " + var + " be " + variable[0] + " ⇸ " + variable[1];
            writer.println(inputSetence + " which is " + rootNode.getPrintString(rootNode));

        } catch (Exception invalidFunctionSpace) {
            String inputSetence = "Let " + var + " be " + variable[0] + " ⇸ " + variable[1];
            writer.println(inputSetence + " which has no value");
        }
        return valuesMap;
    }

    private static HashMap<String, ValueNode> createPair(ValueNode input, ValueNode output,
            HashMap<String, ValueNode> valuesMap, String var) {

        ValueNode pair = new ValueNode("pair");

        ArrayList list = new ArrayList();
        ArrayList list2 = new ArrayList();

        list.add(input.value.get(0));
        list2.add(output.value.get(0));

        pair.addChild(list, valuesMap);
        pair.addChild(list2, valuesMap);

        valuesMap.put(var, pair);

        return valuesMap;
    }

    private static HashMap<String, ValueNode> handleInverse(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        ValueNode rootNode = new ValueNode("set");

        String variable = null;

        try {

            // loops through getting each pair value from the set
            for (int i = 0; i < valueArray.size(); i++) {

                JSONObject variableToInverseObject = (JSONObject) valueArray.get(i);

                variable = (String) variableToInverseObject.get("variable");

                ValueNode setOfPairs = (ValueNode) valuesMap.get(variable);

                // Loops through each pair value, and creates a new pair by revising the input
                // and output
                for (int y = 0; y < setOfPairs.getChildren().size(); y++) {

                    ValueNode pairValueVar = (ValueNode) setOfPairs.getChildren().get(y);

                    ValueNode pairValue = valuesMap.get(pairValueVar.value.get(0));

                    ValueNode inputPair = (ValueNode) pairValue.getChildren().get(0);
                    ValueNode outputPair = (ValueNode) pairValue.getChildren().get(1);

                    // The way the program is designed requires each pair/set having a variable, so
                    // one is assigned
                    varnum = varnum + 1;
                    String pairVar = "addedpair" + varnum;

                    valuesMap = createPair(outputPair, inputPair, valuesMap, pairVar);

                    ArrayList list = new ArrayList();

                    ValueNode pair = valuesMap.get(pairVar);

                    list.add("addedpair" + varnum);

                    rootNode.addChild(list, valuesMap);

                }
                valuesMap.put(var, rootNode);

                String inputSetence = "Let " + var + " be @inverse(" + variable + ")";
                writer.println(inputSetence + " which is " + rootNode.getPrintString(rootNode));

            }

        } catch (Exception invalidInverse) {
            String inputSetence = "Let " + var + " be @inverse(" + variable + ")";
            writer.println(inputSetence + " which has no value");
        }
        return valuesMap;

    }

    // This will return the value of the domain values contained in a set
    private static HashMap<String, ValueNode> handleDomain(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        String varInDomain = null;

        ValueNode rootNode = new ValueNode("set");

        // Treeset is used to add the print out values of the domain to ensure they are
        // ordered properly with duplicates removed
        TreeSet<String> set = new TreeSet<>();

        // Map is used to connect the print out values held in set back to their
        // variable values which will be used to build the final tree
        HashMap<String, String> setMap = new HashMap<String, String>();

        try {

            for (int i = 0; i < valueArray.size(); i++) {

                JSONObject variableToUnionObject = (JSONObject) valueArray.get(i);

                varInDomain = (String) variableToUnionObject.get("variable");

                // Gets the node value of each set from its var
                ValueNode setOfPairValues = valuesMap.get(varInDomain);

                // Loops through each set getting its children value which will be members of
                // the final union
                for (int y = 0; y < setOfPairValues.getChildren().size(); y++) {

                    ValueNode pairValueVar = (ValueNode) setOfPairValues.getChildren().get(y);

                    ValueNode pairValue = valuesMap.get(pairValueVar.value.get(0));

                    ValueNode domainValue = (ValueNode) pairValue.getChildren().get(0);

                    // adds string value to set to organise and remove duplicates
                    set.add(pairValue.getPrintString(domainValue));

                    // adds to map so the string ouput from set can be used to reobtain the variable
                    // value
                    setMap.put(pairValue.getPrintString(domainValue), (String) pairValueVar.value.get(0));

                }

            }

            int setSize = set.size();

            // loops through set removing values, then obtaining the original variable value
            // to add to the final tree
            for (int i = 0; i < setSize; i++) {

                ArrayList valueToAdd = new ArrayList();

                // Gets variable value from map by removing value from set
                String variableValue = setMap.get(set.pollFirst());

                ValueNode domainNode = (ValueNode) valuesMap.get(variableValue).getChildren().get(0);

                // adds the domainValue to the set node
                valueToAdd.add(domainNode.value.get(0));
                rootNode.addChild(valueToAdd, valuesMap);
            }

            valuesMap.put(var, rootNode);

            // handles print out
            String inputSetence = "Let " + var + " be @dom(" + varInDomain + ")";
            writer.println(inputSetence + " which is " + rootNode.getPrintString(rootNode));

        } catch (Exception invalidDomain) {
            String inputSetence = "Let " + var + " be @dom(" + varInDomain + ")";
            writer.println(inputSetence + " which has no value");
        }
        return valuesMap;

    }

    private static HashMap<String, ValueNode> handleUnion(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        ValueNode rootNode = new ValueNode("set");

        // Treeset is used to add the print out values of the final union to ensure they
        // are ordered properly with duplicates removed
        TreeSet<String> set = new TreeSet<>();

        // Map is used to connect the print out values held in set back to their
        // variable values which will be used to build the final tree
        HashMap<String, String> setMap = new HashMap<String, String>();

        // holds the vars which hold the sets which are being union-ed
        JSONObject[] variableToUnionObject = new JSONObject[2];

        try {

            for (int i = 0; i < valueArray.size(); i++) {

                variableToUnionObject[i] = (JSONObject) valueArray.get(i);

                // Gets the node value of each set from its var
                ValueNode variableToUnionNode = valuesMap.get(variableToUnionObject[i].get("variable"));

                // Loops through each set getting its children value which will be members of
                // the final union
                for (int y = 0; y < variableToUnionNode.getChildren().size(); y++) {

                    ValueNode setToUnionVariable = (ValueNode) variableToUnionNode.getChildren().get(y);

                    ValueNode memberOfUnion = valuesMap.get(setToUnionVariable.value.get(0));

                    // adds string value to set to organise and remove duplicates
                    set.add(memberOfUnion.getPrintString(memberOfUnion));

                    // adds to map so the string ouput from set can be used to reobtain the variable
                    // value
                    setMap.put(memberOfUnion.getPrintString(memberOfUnion), (String) setToUnionVariable.value.get(0));

                }

            }

            int setSize = set.size();

            // loops through set removing values, then obtaining the original variable value
            // to add to the final tree
            for (int i = 0; i < setSize; i++) {

                ArrayList valueToAdd = new ArrayList();
                valueToAdd.add(setMap.get(set.pollFirst()));
                rootNode.addChild(valueToAdd, valuesMap);

            }

            valuesMap.put(var, rootNode);

            // handles print out
            String inputSetence = "Let " + var + " be " + variableToUnionObject[0].get("variable") + " ∪ "
                    + variableToUnionObject[1].get("variable");
            writer.println(inputSetence + " which is " + rootNode.getPrintString(rootNode));

        } catch (Exception invalidUnion) {
            String inputSetence = "Let " + var + " be " + variableToUnionObject[0].get("variable") + " ∪ "
                    + variableToUnionObject[1].get("variable");
            writer.println(inputSetence + " which has no value");
        }

        return valuesMap;

    }

    private static HashMap<String, ValueNode> applyFunction(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        ValueNode rootNode = null;

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        // String value checking the set of pairs
        String appliedValue = "";

        // Object of value checking the set of pairs
        JSONObject applyValue = null;

        // set being checked
        JSONObject setVar = null;

        try {

            try {

                applyValue = (JSONObject) valueArray.get(1);

                ValueNode applyValueNode = valuesMap.get(applyValue.get("variable"));

                appliedValue = applyValueNode.getPrintString(applyValueNode);

            } catch (Exception isLong) {
                long appliedValueLong = (long) valueArray.get(1);
                appliedValue = Long.toString(appliedValueLong);
            }

            setVar = (JSONObject) valueArray.get(0);

            // holds the actual values of the set
            ValueNode setNode = valuesMap.get(setVar.get("variable"));

            rootNode = applyFunctionHelper(setNode, appliedValue);

            valuesMap.put(var, rootNode);

            // handles the writing to file
            String inputSetence = "Let " + var + " be " + setVar.get("variable") + " " + applyValue.get("variable");
            writer.println(inputSetence + " which is " + rootNode.getPrintString(rootNode));

        } catch (Exception invalidFunction) {
            String inputSetence = "Let " + var + " be " + setVar.get("variable") + " " + applyValue.get("variable");
            writer.println(inputSetence + " which has no value");
        }

        return valuesMap;

    }

    private static ValueNode applyFunctionHelper(ValueNode setNode, String appliedValue) {

        // Loops through children getting their values. if a match is found then set
        // rootNode and break
        for (int i = 0; i < setNode.getChildren().size(); i++) {

            ValueNode pairVar = (ValueNode) setNode.getChildren().get(i);

            ValueNode pairVal = (ValueNode) pairVar.getChildren().get(0);

            ValueNode pairInputVal = (ValueNode) pairVal.getChildren().get(0);

            String pairInputValString = pairInputVal.getPrintString(pairInputVal);

            if (pairInputValString.equals(appliedValue)) {

                ValueNode returnNode = (ValueNode) pairVal.getChildren().get(1);

                return returnNode;
            }

        }
        return null;
    }

    private static HashMap<String, ValueNode> isFunction(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        // assume is not function until proven otherwise
        int resultValue = 0;

        // holds the variable for the set of input/output pairs
        JSONObject graphVar = null;

        try {

            JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

            graphVar = (JSONObject) valueArray.get(0);

            // holds the set of input/output pairs
            ValueNode graphNode = valuesMap.get(graphVar.get("variable"));

            // if it is not a set through exception as is not valid
            if (!graphNode.type.equals("set"))
                throw new Exception();

            // Inputs will be added to this to remove duplicates
            // if duplicates exist then it is not a function as
            // each input must point to exactly one output
            TreeSet<Long> set = new TreeSet<>();

            // loops through set of input/output pairs getting each one ensuring they're
            // valid and adding the inputs to a treeset
            for (int i = 0; i < graphNode.children.size(); i++) {

                ValueNode pairVariable = (ValueNode) graphNode.children.get(i);
                ValueNode pairValue = valuesMap.get(pairVariable.value.get(0));

                if (!pairValue.type.equals("pair"))
                    throw new Exception();

                ValueNode val = (ValueNode) pairValue.getChildren().get(0);
                set.add((Long) val.value.get(0));

            }
            // if the size of the set is equal to the original number of children
            // no duplicate inputs exist thus its a valid function
            if (set.size() == graphNode.children.size())
                resultValue = 1;

            ValueNode rootNode = new ValueNode("long");

            ArrayList<Long> valArr = new ArrayList<Long>();

            valArr.add(Long.valueOf(resultValue));

            rootNode.addChild(valArr, valuesMap);

            valuesMap.put(var, rootNode);

            // handles the writing to file
            String inputSetence = "Let " + var + " be @is-function(" + graphVar.get("variable") + ")";
            writer.println(inputSetence + " which is " + resultValue);

        } catch (Exception invalidFunction) {
            String inputSetence = "Let " + var + " be @is-function(" + graphVar.get("variable") + ")";
            writer.println(inputSetence + " which is " + resultValue);
        }

        return valuesMap;

    }

    // this method will handle membership tests, create the tree for the result, and
    // write result to file
    private static HashMap<String, ValueNode> handleMember(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        ValueNode rootNode = new ValueNode("long");

        // used to add value to rootNode
        ArrayList value = new ArrayList();

        // holds the variable value to check if member
        String checkMember = "";
        // holds the variable to write out
        String value1Var = "";

        // Can only be a set value as ints/pairs will not allow for member check
        ValueNode theNode = new ValueNode("set");

        // handles var
        try {
            JSONObject var2 = (JSONObject) valueArray.get(0);
            value1Var = (String) var2.get("variable");
            ValueNode chechMemberNode = valuesMap.get(value1Var);
            checkMember = chechMemberNode.getPrintString(chechMemberNode);
            // handles long
        } catch (Exception isLong) {
            long varVal = (long) valueArray.get(0);
            value1Var = String.valueOf(varVal);
            checkMember = String.valueOf(varVal);
        }

        // The variable being checked for member
        String varVal = "";
        // will hold the result of member to be added to rootNode
        int memberResult = 0;

        // will try to handle true results
        try {
            JSONObject var2 = (JSONObject) valueArray.get(1);
            varVal = (String) var2.get("variable");

            theNode = valuesMap.get(varVal);

            // can only check member for set values
            if (!theNode.type.equals("set")) {

                throw new Exception("Exception thrown");
            }

            // will do the actual checking of membership
            boolean member = checkMember(checkMember, theNode);

            if (!member) {
                throw new Exception("Exception thrown");
            }

            value.add((long) 1);
            memberResult = 1;

            // handles false results
        } catch (Exception falseResult) {
            value.add((long) 0);
            memberResult = 0;
        }

        String inputSetence = "Let " + var + " be " + value1Var + " \u2208 " + varVal;
        writer.println(inputSetence + " which is " + memberResult);

        rootNode.addChild(value, valuesMap);

        valuesMap.put(var, rootNode);

        return valuesMap;

    }

    private static boolean checkMember(String memberTest, ValueNode theNode) {

        // gets all the children nodes to check and stores in list
        ArrayList list = new ArrayList();
        list.addAll(theNode.getChildren());

        // checks all children nodes for equality, if a match then return true
        while (!list.isEmpty()) {

            ValueNode node = (ValueNode) list.remove(0);

            if (memberTest.equals(node.getPrintString(node))) {
                return true;
            }
        }
        return false;
    }

    // will handle equality tests, create the tree for the result and write to file
    private static HashMap<String, ValueNode> handleEquality(String var, String operator, JSONObject valueJSON,
            HashMap<String, ValueNode> valuesMap, PrintWriter writer) {

        JSONArray valueArray = (JSONArray) valueJSON.get("arguments");

        // holds the values to be placed into nodes
        ArrayList values[] = new ArrayList[valueArray.size()];

        ValueNode[] nodes = new ValueNode[valueArray.size()];

        ValueNode rootNode = new ValueNode("long");

        // holds the variables whose equality will be checked
        ArrayList inputVars = new ArrayList();

        // Creates/gets the nodes we will be comparing
        for (int i = 0; i < valueArray.size(); i++) {
            values[i] = new ArrayList();
            // handles long
            try {
                long varVal = (long) valueArray.get(i);
                inputVars.add(varVal);
                // adds value to array to be added to node
                values[i].add(varVal);

                nodes[i] = new ValueNode("long");
                nodes[i].addChild(values[i], valuesMap);

                // handles var
            } catch (Exception isVar) {

                JSONObject var2 = (JSONObject) valueArray.get(i);
                String varVal = (String) var2.get("variable");

                inputVars.add(varVal);

                // can get already existing var value from the map
                nodes[i] = (ValueNode) valuesMap.get(varVal);

            }

        }

        String nodePrint1 = nodes[0].getPrintString(nodes[0]);
        String nodePrint2 = nodes[1].getPrintString(nodes[1]);

        long val;

        // used to add value to node
        ArrayList<Long> value = new ArrayList<Long>();

        // if equal then set value to 1 else 0
        if (nodePrint1.compareTo(nodePrint2) == 0) {
            val = 1;
            value.add(val);
        } else {
            val = 0;
            value.add(val);
        }

        // add to the node
        rootNode.addChild(value, valuesMap);

        valuesMap.put(var, rootNode);

        // handle the print out to file
        String inputSetence = "Let " + var + " be " + inputVars.get(0) + " = " + inputVars.get(1);
        writer.println(inputSetence + " which is " + val);

        return valuesMap;

    }

}

