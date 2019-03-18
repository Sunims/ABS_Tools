/**
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.erlang;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import abs.common.CompilerUtils;

import abs.backend.common.CodeStream;
import abs.backend.erlang.ErlUtil.Mask;
import abs.frontend.antlr.parser.ABSParser.Pure_expContext;
import abs.frontend.ast.*;
import abs.frontend.typechecker.Type;

import com.google.common.collect.Iterables;

import java.nio.charset.Charset;

import org.apache.commons.io.output.WriterOutputStream;

/**
 * Generates the Erlang module for one class
 *
 * @author Georg Göri
 *
 */
public class ClassGenerator {
    private final CodeStream ecs;
    private final ClassDecl classDecl;
    private final String modName;
    private final boolean hasFields;

    public ClassGenerator(ErlApp ea, ClassDecl classDecl) throws IOException {
        this.classDecl = classDecl;
        modName = ErlUtil.getName(classDecl);
        ecs = ea.createSourceFile(modName);
        hasFields = classDecl.getParams().hasChildren() || classDecl.getFields().hasChildren() || classDecl.hasPhysical();
        try {
            generateHeader();
            generateExports();
            generateConstructor();
            generateRecoverHandler();
            generateMethods();
            generateDataAccess();
            generatePhysical();
        } finally {
            ecs.close();
        }
    }

    private void generateHeader() {
        ecs.pf("-module(%s).", modName);
        ecs.println("-include_lib(\"../include/abs_types.hrl\").");
        if (hasFields) {
            ecs.println("-behaviour(object).");
        }
    }

    private void generateMethods() {
        for (MethodImpl m : classDecl.getMethodList()) {
            ecs.pf(" %%%% %s:%s", m.getFileName(), m.getStartLine());
            MethodSig ms = m.getMethodSig();
            ecs.pf(" %%%% %s:%s", m.getFileName(), m.getStartLine());
            ErlUtil.functionHeader(ecs, "m_" + ms.getName(), generatorClassMatcher(), ms.getParamList());
            ecs.print("put(vars, #{ 'this' => O");
            for (ParamDecl p : ms.getParamList()) {
                // Same name construction as
                // ErlUtil.functionHeader(CodeStream, String, List<String>, Mask)
                ecs.format(",%n '%s' => %s", p.getName(), ErlUtil.absParamDeclToErlVarName(p));
            }
            ecs.println(" }),");
            ecs.println("try");
            ecs.incIndent();
            
            if(classDecl.hasPhysical())
            {
                ArrayList<String> physicalFields = new ArrayList<String>();
               
                for (FieldDecl fieldDecl : classDecl.getPhysical().getFieldList()) {
                    physicalFields.add(fieldDecl.getName());
                } 
                    
               ASTNode node = m.getBlock().lookup_await(m.getBlock(), physicalFields);
               if(node instanceof FieldUse)
               {
                   ecs.println("put(vars, (get(vars))#{'output_file_physical' =>   iolist_to_binary([\"output_ \", builtin:toString(Cog,maps:get('this', get(vars))) ,\".txt\"]) }),  ");      
                   ecs.println("case cmp:lt(get(O,'t_start_physical'),0) of");          
                   ecs.incIndent();
                   ecs.println("true -> ");     
                   ecs.println("set(O,'t_start_physical',m_ABS_StdLib_funs:f_timeValue(Cog,m_ABS_StdLib_funs:f_now(Cog,[O,DC|lists:map(fun({_, X}) -> X end, maps:to_list(get(vars))) ++ Stack]),[O,DC|lists:map(fun({_, X}) -> X end, maps:to_list(get(vars))) ++ Stack])),");      
                   ecs.println("file:write_file(maps:get('output_file_physical', get(vars)), io_lib:fwrite(\"~p\\n\", [m_ABS_StdLib_funs:f_timeValue(Cog,m_ABS_StdLib_funs:f_now(Cog,[O,DC|lists:map(fun({_, X}) -> X end, maps:to_list(get(vars))) ++ Stack]),[O,DC|lists:map(fun({_, X}) -> X end, maps:to_list(get(vars))) ++ Stack])]), [write]);");      
                   ecs.println("false ->   ok ");             
                   ecs.decIndent();
                   ecs.println("end, ");         
               }               
            }
            
            Vars vars = new Vars();
            m.getBlock().generateErlangCode(ecs, vars);
            ecs.println();
            ecs.decIndent().println("catch");
            ecs.incIndent();
            ecs.println("_:Exception ->");
            if (classDecl.hasRecoverBranch()) {
                ecs.incIndent();
                ecs.println("Recovered = try 'recover'(O, Exception) catch _:RecoverError -> io:format(standard_error, \"Recovery block for ~s in class " + classDecl.qualifiedName() + " failed with exception ~s~n\", [builtin:toString(Cog, Exception), builtin:toString(Cog, RecoverError)]), false end,");
                ecs.println("case Recovered of");
                ecs.incIndent().println("true -> exit(Exception);");
                ecs.println("false ->");
                ecs.incIndent();
                ecs.println("io:format(standard_error, \"Uncaught ~s in method " + ms.getName() + " not handled successfully by recovery block, killing object ~s~n\", [builtin:toString(Cog, Exception), builtin:toString(Cog, O)]),");
                ecs.println("object:die(O, Exception), exit(Exception)");
                ecs.decIndent().println("end");
                ecs.decIndent();
            } else {
                ecs.incIndent();
                ecs.println("io:format(standard_error, \"Uncaught ~s in method " + ms.getName() + " and no recovery block in class definition, killing object ~s~n\", [builtin:toString(Cog, Exception), builtin:toString(Cog, O)]),");
                ecs.println("object:die(O, Exception), exit(Exception)");
                ecs.decIndent();
            }
            ecs.decIndent().println("end.");
            ecs.decIndent();
        }

    }

    private void generateConstructor() {
        ErlUtil.functionHeaderParamsAsList(ecs, "init", generatorClassMatcher(), classDecl.getParamList(), Mask.none);
        ecs.println("put(vars, #{}),");
        Vars vars = Vars.n();
        for (ParamDecl p : classDecl.getParamList()) {
            ecs.pf("set(O,'%s',%s),", p.getName(), "P_" + p.getName());
        }
        for (FieldDecl p : classDecl.getFields()) {
            ErlUtil.emitLocationInformation(ecs, p.getModel(), p.getFileName(),
                                            p.getStartLine(), p.getEndLine());
            if (p.hasInitExp()) {
                ecs.format("set(O,'%s',", p.getName());
                p.getInitExp().generateErlangCode(ecs, vars);
                ecs.println("),");
            }
        }
        if (classDecl.getInitBlock() != null) {
            classDecl.getInitBlock().generateErlangCode(ecs, vars);
            ecs.println(",");
        }
        if(classDecl.getPhysical() != null)        {
            classDecl.getPhysical().generateErlangCode(ecs, vars);                   
            ecs.println(",");             
        }
        if (classDecl.isActiveClass()) {
            ecs.println("cog:process_is_blocked_for_gc(Cog, self()),");
            ecs.print("cog:add_sync(Cog,active_object_task,O,#process_info{method= <<\"run\"/utf8>>},");
            ecs.print(vars.toStack());
            ecs.println("),");
            ecs.println("cog:process_is_runnable(Cog,self()),");
            ecs.print("task:wait_for_token(Cog,");
            ecs.print(vars.toStack());
            ecs.println("),");
        }
        ecs.println("O.");
        ecs.decIndent();
    }

    private void generateRecoverHandler() {
        if (classDecl.hasRecoverBranch()) {
            Vars vars = new Vars();
            Vars safe = vars.pass();
            // Build var scopes and statmemnts for each branch
            java.util.List<Vars> branches_vars = new java.util.LinkedList<>();
            java.util.List<String> branches = new java.util.LinkedList<>();
            for (CaseBranchStmt b : classDecl.getRecoverBranchs()) {
                Vars v = vars.pass();
                StringWriter sw = new StringWriter();
                CodeStream buffer = new CodeStream(new WriterOutputStream(sw, Charset.forName("UTF-8")),"");
                b.getLeft().generateErlangCode(ecs, buffer, v);
                buffer.setIndent(ecs.getIndent());
                buffer.println("->");
                buffer.incIndent();
                b.getRight().generateErlangCode(buffer, v);
                buffer.println(",");
                buffer.print("true");
                buffer.decIndent();
                buffer.close();
                branches_vars.add(v);
                branches.add(sw.toString());
                vars.updateTemp(v);
            }
            ErlUtil.functionHeader(ecs, "recover", ErlUtil.Mask.none, generatorClassMatcher(), "Exception");
            ecs.println("Result=case Exception of ");
            ecs.incIndent();
            // Now print statments and mergelines for each branch.
            java.util.List<String> mergeLines = vars.merge(branches_vars);
            Iterator<String> ib = branches.iterator();
            Iterator<String> im = mergeLines.iterator();
            while (ib.hasNext()) {
                ecs.print(ib.next());
                ecs.incIndent();
                ecs.print(im.next());
                ecs.println(";");
                ecs.decIndent();
            }
            ecs.println("_ -> false");
            ecs.decIndent();
            ecs.print("end");
            ecs.println(".");
            ecs.decIndent();
        }
    }

    private String generatorClassMatcher() {
        return String.format("O=#object{class=%s=C,ref=Ref,cog=Cog=#cog{ref=CogRef,dc=DC}}", modName);
    }

    private void generateDataAccess() {
        // FIXME: we should eliminate 'set', 'get' and directly use
        // object:set_field_value / object:get_field_value instead
        ErlUtil.functionHeader(ecs, "set", Mask.none,
                String.format("O=#object{class=%s=C,ref=Ref,cog=Cog}", modName), "Var", "Val");
        ecs.println("object:set_field_value(O, Var, Val).");
        ecs.decIndent();
        ecs.println();
        ErlUtil.functionHeader(ecs, "get", Mask.none, generatorClassMatcher(), "Var");
        ecs.println("object:get_field_value(O,Var).");
        ecs.decIndent();
        ecs.println();
        
        // generate data access for physical block
        List<FieldDecl> physicalFieldLst = null;
        List<FieldDecl> physicalAdditionalFieldLst = new List<FieldDecl>();
        if(classDecl.hasPhysical())     
        {
            physicalFieldLst = classDecl.getPhysical().getFieldList();
            
            FieldDecl t_start_physical = new FieldDecl();            
            t_start_physical.setName("t_start_physical");
            t_start_physical.setInitExp(new IntLiteral("-1"));
            physicalAdditionalFieldLst.insertChild(t_start_physical, 0);
            
        }
        else        
            physicalFieldLst = new List<FieldDecl>();
        
        ecs.print("-record(state,{");        
            
        boolean first = true;
        for (TypedVarOrFieldDecl f : Iterables.concat(classDecl.getParams(), classDecl.getFields(), physicalFieldLst, physicalAdditionalFieldLst)) {
            if (!first)
                ecs.print(",");
            first = false;
            ecs.format("'%s'=null", f.getName());
        }
        ecs.println("}).");
        ErlUtil.functionHeader(ecs, "init_internal");
        ecs.println("#state{}.");
        ecs.decIndent();
        ecs.println();
        for (TypedVarOrFieldDecl f : Iterables.concat(classDecl.getParams(), classDecl.getFields(), physicalFieldLst, physicalAdditionalFieldLst)) {
            ecs.pf(" %%%% %s:%s", f.getFileName(), f.getStartLine());
            ErlUtil.functionHeader(ecs, "get_val_internal", Mask.none, String.format("#state{'%s'=G}", f.getName()),
                                   "'" + f.getName() + "'");
            ecs.println("G;");
            ecs.decIndent();
        }
        ErlUtil.functionHeader(ecs, "get_val_internal", Mask.none, "_", "_");
        ecs.println("%% Invalid return value; handled by HTTP API when querying for non-existant field.");
        ecs.println("%% Will never occur in generated code.");
        ecs.println("none.");
        ecs.decIndent();
        ecs.println();
        if (hasFields) {
            first = true;
            for (TypedVarOrFieldDecl f : Iterables.concat(classDecl.getParams(), classDecl.getFields(), physicalFieldLst, physicalAdditionalFieldLst)) {
                if (!first) {
                    ecs.println(";");
                    ecs.decIndent();
                }
                first = false;
                ecs.pf(" %%%% %s:%s", f.getFileName(), f.getStartLine());
                ErlUtil.functionHeader(ecs, "set_val_internal", Mask.none, "S", "'" + f.getName() + "'", "V");
                ecs.format("S#state{'%s'=V}", f.getName());
            }
            ecs.println(".");
            ecs.decIndent();
            ecs.println();
        } else {
            // Generate failing Dummy
            ErlUtil.functionHeader(ecs, "set_val_internal", Mask.none, "S", "S", "S");
            ecs.println("throw(badarg).");
            ecs.decIndent();
        }        
        ErlUtil.functionHeader(ecs, "get_all_state", Mask.none, "S");
        ecs.println("[");
        ecs.incIndent();
        first = true;
        for (TypedVarOrFieldDecl f : Iterables.concat(classDecl.getParams(), classDecl.getFields(), physicalFieldLst, physicalAdditionalFieldLst)) {
            if (!first) ecs.print(", ");
            first = false;
            ecs.pf("{ '%s', S#state.%s }",
                   f.getName(), f.getName());
        }
        ecs.decIndent();
        ecs.println("].");
    }

    private void generateExports() {
        ecs.println("-export([get_val_internal/2,set_val_internal/3,init_internal/0,get_all_state/1]).");
        ecs.println("-compile(export_all).");
        ecs.println();

        HashSet<MethodSig> callable_sigs = new HashSet<>();
        HashSet<InterfaceDecl> visited = new HashSet<>();
        for (InterfaceTypeUse i : classDecl.getImplementedInterfaceUseList()) {
            visited.add((InterfaceDecl)i.getDecl());
        }

        while (!visited.isEmpty()) {
            InterfaceDecl id = visited.iterator().next();
            visited.remove(id);
            for (MethodSig ms : id.getBodyList()) {
                if (ms.isHTTPCallable()) {
                    callable_sigs.add(ms);
                }
            }
            for (InterfaceTypeUse i : id.getExtendedInterfaceUseList()) {
                visited.add((InterfaceDecl)i.getDecl());
            }
        }


        ecs.print("exported() -> #{ ");
        boolean first = true;
        for (MethodSig ms : callable_sigs) {
            if (ms.isHTTPCallable()) {
                if (!first) ecs.print(", ");
                first = false;
                ecs.print("<<\"" + ms.getName() + "\">> => { ");
                ecs.print("'m_" + ms.getName() + "'");
                ecs.print(", ");
                ecs.print("<<\"" + ms.getReturnType().getType().getQualifiedName() + "\">>");
                ecs.print(", ");
                ecs.print("[ ");
                boolean innerfirst = true;
                for (ParamDecl p : ms.getParamList()) {
                    if (!innerfirst) ecs.print(", ");
                    innerfirst = false;
                    ecs.print("{ ");
                    ecs.print("<<\"" + p.getName() + "\">>");
                    ecs.print(", ");
                    ecs.print("<<\"" + p.getAccess().getType().getQualifiedName() + "\">>");
                    ecs.print(" }");
                }
                ecs.print("] ");
                ecs.print("}");
            }
        }
        ecs.println(" }.");
        ecs.println();
    }
    


    private void generatePhysical() {
     // generate data access for physical block
        ecs.println();
        List<FieldDecl> physicalFieldLst = null;
        if(classDecl.hasPhysical())
        {                                
            physicalFieldLst = classDecl.getPhysical().getFieldList();       
            
            //ErlUtil.functionHeader(ecs, "start_maxima", generatorClassMatcher());
            ecs.pf("'solve_differential_equation'(%s, Condition, PrintFunction)->", generatorClassMatcher());            
            ecs.print("put(vars, (get(vars))#{ ");
            ecs.format("'Condition' => Condition");    
            ecs.format(", 'PrintFunction' => PrintFunction"); 
            ecs.println(" }),");        
            ecs.println("try ");
            ecs.incIndent();
            ecs.println("put(vars, (get(vars))#{'output_file_physical' =>   iolist_to_binary([\"output_ \", builtin:toString(Cog,maps:get('this', get(vars))) ,\".txt\"]) }), "); 
            
                        
            Vars vars = new Vars();
            Vars v=vars.pass();
            
            //-----------------------------------------------------
            // define parameter and equations
            ecs.print("put(vars, (get(vars))#"); 
            ecs.pf("{'maximaVal' => iolist_to_binary([" );
            boolean first = true;
            
            ecs.println("\"t0:\", builtin:toString(Cog,get(O,'t_start_physical')), \" \\$ \"");                       
            
            if(classDecl.getPhysical().getSolveType() == 1)
            {
                for (FieldDecl p : physicalFieldLst) {
                    if (p.hasInitExp()) {    
                        ecs.print(",");
                        
                        if(p.getInitExp() instanceof DifferentialExp){                       
                            DifferentialExp diff = (DifferentialExp)p.getInitExp();                             
                            ecs.print("\"dgl_" + p.getName());                            
                            ecs.print(": ");
                            diff.getLeft().generateMaximaCode(ecs, vars);
                            ecs.print("= ");
                            diff.getRight().generateMaximaCode(ecs, vars);
                            ecs.println(" \\$ \"");
                            ecs.pf(",\" e_%s : subst ( %s, %s(t), dgl_%s ) \\$ \" ", p.getName(), p.getName(), p.getName(), p.getName());
                            ecs.pf(",\"ode_%s : ode2(e_%s,%s,t) \\$ \"", p.getName(), p.getName(), p.getName());
                            ecs.pf(",\"%s0:\", builtin:toString(Cog,get(O,'%s')), \" \\$ \"", p.getName(), p.getName());
//                            ecs.pf(",\"%s : second(ic1(ode_%s,t=t0,%s=%s0)) \\$ \"", p.getName(), p.getName(), p.getName(), p.getName());
                            ecs.pf(",\"%s : second(first(solve(ic1(ode_%s,t=t0,%s=%s0), %s))) \\$ \"", p.getName(), p.getName(), p.getName(), p.getName(), p.getName());                                                               
                                                           
                        }
                        else
                        {
                            //TODO MKE: fehler falls falscher Typ???
                            ecs.pf("\"%s:\", builtin:toString(Cog,get(O,'%s')), \" \\$ \"", p.getName(), p.getName());
                        }
                    }                
                }
            }
            else
            {
                writeIntialFunc(physicalFieldLst, vars);
            }
            
            //minimize t maxima
            ecs.println(",\"fpprintprec:5 \\$ \" ");
            ecs.println(",\"ratprint : false \\$ \" ");
            ecs.println(",\"load(fmin_cobyla) \\$ \" ");
            //ecs.println(",\"t_min: fmin_cobyla(t, [t], [t0+5],  constraints = [\", Condition ,\"], iprint=0) \\$ \" ");
            ecs.println(",\"t_min: fmin_cobyla(t, [t], [t0+5],  constraints = [\", Condition ,\"]) \\$ \" ");
            ecs.println(",\"t_min:float(second(first(first(t_min)))) \\$ \" ");
            ecs.println(",\"ratnumer (rat(float(t_min)));\" ");
            ecs.println(",\"denom (rat(float(t_min)));\" ");  
            
            //get current state 
            for (FieldDecl p : physicalFieldLst) {
                if (p.hasInitExp()) {                        
                    if(p.getInitExp() instanceof DifferentialExp){                                                
                        ecs.pf(",\"%s_tmp: subst ( t_min, t, %s) \\$ \" ", p.getName(), p.getName());
                        ecs.pf(",\"ratnumer (rat(float(%s_tmp)));\"", p.getName());
                        ecs.pf(",\"denom (rat(float(%s_tmp)));\"", p.getName());                        
                        ecs.pf(",\"string(%s);\"", p.getName());
                    }
                }
            }
            ecs.println(",\"quit()\\$\"");
            ecs.println("])}),");            
            
            ecs.println("put(vars, (get(vars))#{'command' => iolist_to_binary([\"python start_maxima.py \",\" \\\" \" ,  maps:get('maximaVal', get(vars)),\" \\\" \"])}),");
            ecs.println("%builtin:println(Cog, builtin:toString(Cog, maps:get('command', get(vars)) )),");
            ecs.println("MaximaOut = os:cmd(io_lib:format(\"~s\",[maps:get('command', get(vars))])),");
            ecs.println("ResultLst = re:split(MaximaOut, \"\\n\",[{return,list}]),");
            
            //-----------------------------------------------------
            //read values    
            ecs.println("T_1 = lists:nth(1, ResultLst),");
            ecs.println("T_1_int = list_to_integer(T_1),");
            ecs.println("T_2 = lists:nth(2, ResultLst),");
            ecs.println("T_2_int = list_to_integer(T_2),");

            // read values
            int index = 3;
            first = true;
            ecs.println("case maps:get('PrintFunction', get(vars)) of");
            ecs.println("true -> ");
                 
            ecs.incIndent();
            for (FieldDecl p : physicalFieldLst) {
                if (p.hasInitExp()) {  
                    if(p.getInitExp() instanceof DifferentialExp){ 
                        if(first)
                            first = false;
                        else
                            ecs.print(",");
                        ecs.pf("T_%d = lists:nth(%d, ResultLst),", index, index);
                        ecs.pf("T_%d_int = list_to_integer(T_%d ),", index, index);
                        
                        ecs.pf("T_%d = lists:nth(%d, ResultLst),", index+1, index+1);
                        ecs.pf("T_%d_int = list_to_integer(T_%d ),",index+1, index+1);                                                
                        
                        

                        ecs.pf("file:write_file(maps:get('output_file_physical', get(vars)), io_lib:fwrite(\"~p\\n\", [lists:nth(%d, ResultLst)]), [append]),", index+2);                        
                        ecs.pf("set(O,'%s',rationals:rdiv(T_%d_int,T_%d_int))", p.getName(), index, index+1);
                        
                        index=index+3; 
                    }                    
                }
            }     
            ecs.print(";");
            ecs.println("false -> ok");  
            ecs.decIndent();
            ecs.println("end,");
            
            ecs.println("case cmp:le(rationals:rdiv(T_1_int,T_2_int), get(O,'t_start_physical')) of");
            ecs.println("true -> rationals:add(get(O,'t_start_physical'), rationals:rdiv(1,100));"); // dass der Task unterbrochen werden kann
            ecs.println("false -> rationals:rdiv(T_1_int,T_2_int)"); 
            ecs.println("end ");
            ecs.println("catch ");
            ecs.println("_:Exception -> ");
            ecs.println("io:format(standard_error, \"Uncaught ~s in method solve_differential_equation and no recovery block in class definition, killing object ~s~n\", [builtin:toString(Cog, Exception), builtin:toString(Cog, maps:get('command', get(vars)))]) ");
            ecs.println("end.");
            ecs.decIndent();
            ecs.decIndent();
            ecs.println();
        }      
    }
    
    private void writeIntialFunc(List<FieldDecl> physicalFieldLst, Vars vars)
    {
        boolean first = true;
        for (FieldDecl p : physicalFieldLst) {
            if (p.hasInitExp()) {    
                ecs.print(",");
                
                if(p.getInitExp() instanceof DifferentialExp){                       
                    DifferentialExp diff = (DifferentialExp)p.getInitExp();                    
                    ecs.pf("\"%s0:\", builtin:toString(Cog,get(O,'%s')), \" \\$ \"", p.getName(), p.getName());
                    ecs.print("\"e_" + p.getName());
                    ecs.print(": ");
                    diff.getLeft().generateMaximaCode(ecs, vars);
                    ecs.print("= ");
                    diff.getRight().generateMaximaCode(ecs, vars);
                    ecs.println(" \\$ \"");
                    ecs.pf(",\"atvalue(%s(t),t=0, %s0) \\$ \"", p.getName(), p.getName());                                            
                }
                else
                {
                    //TODO MKE: fehler falls falscher Typ???
                    ecs.pf("\"%s:\", builtin:toString(Cog,get(O,'%s')), \" \\$ \"", p.getName(), p.getName());
                }
            }                
        }
        
        //-----------------------------------------------------
        // write code for solve dgl
        first = true; 
        ecs.print(",\"sol: desolve([" );            
        for (FieldDecl p : physicalFieldLst) {
            if(p.getInitExp() instanceof DifferentialExp){ 
                if (first) 
                    first = false;
                else
                    ecs.print(",");
                    
                if(p.getInitExp() instanceof DifferentialExp){                       
                    ecs.print("e_" + p.getName());                   
                }    
            }
        }
        ecs.print("],[");
        
        first = true;   
        int numberOfDgl = 0;
        for (FieldDecl p : physicalFieldLst) {
            if(p.getInitExp() instanceof DifferentialExp){ 
                if (first) 
                    first = false;
                else
                    ecs.print(",");
                    
                if(p.getInitExp() instanceof DifferentialExp){                       
                    ecs.print(p.getName()+"(t)");                   
                }        
            }
            numberOfDgl++;
        }
        ecs.println("]) \\$ \" ");
        
        //-----------------------------------------------------
        // write functions definitions
        int counterDGL = 1; // index in maxima starts at 1
        for (FieldDecl p : physicalFieldLst) {
            if(p.getInitExp() instanceof DifferentialExp){ 
                if(numberOfDgl>1)
                    ecs.pf(",\" %s(t) := second(sol[%d]) \\$ \" ", p.getName().replace("(t)", ""), counterDGL);
                else
                    ecs.pf(",\" %s(t) := second(sol) \\$ \" ", p.getName().replace("(t)", "") );
                
                ecs.pf(",\" %s : subst ( t-t0, t, %s(t) ) \\$ \" ", p.getName(), p.getName());
                counterDGL++;
            }
        }
    } 
    
}