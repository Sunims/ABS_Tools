/**
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.scala;

import java.io.PrintWriter;

import abs.common.StringUtils;
import abs.frontend.ast.Decl;
import abs.frontend.ast.HasTypeParameters;
import abs.frontend.ast.List;
import abs.frontend.ast.MethodSig;
import abs.frontend.ast.ParamDecl;
import abs.frontend.ast.ParametricDataTypeDecl;
import abs.frontend.ast.ParametricFunctionDecl;
import abs.frontend.ast.TypeDecl;
import abs.frontend.ast.TypeParameterDecl;
import abs.frontend.typechecker.Type;

/**
 * 
 * @author Andri Saar <andri@cs.ioc.ee>
 *
 */
public class ScalaUtils {
    /**
     * Changes the variable (or parameter) names that are legal in ABS but not in Scala by prepending underscores to them.
     * 
     * @param name the name that is checked for reserved words
     * @return Scala-safe name
     */
    public static String mangleName(String name) {
        if (name.matches("^_*var$") || name.matches("^_*val$") || 
                name.matches("^_*abs$") || name.matches("^_*sender$") ||
                name.matches("^_*log$"))
            return "_" + name;
        
        return name;
    }
    
    public static void generateTypeParameters(Decl decl, PrintWriter writer) {
        List<TypeParameterDecl> typeParams = null;
        
        if (decl instanceof HasTypeParameters) {
            typeParams = ((HasTypeParameters)decl).getTypeParameters();
        } else
            return;
        
        if (typeParams.getNumChild() > 0) {
            writer.print("[");
            boolean b = false;
            
            for (TypeParameterDecl d: typeParams) {
                if (b) writer.print(", ");
                writer.print(d.getName());
                b = true;
            }
            
            writer.print("]");
        }
    }
    
    public static final void generateMessages(Iterable<MethodSig> methods, PrintWriter writer, abs.frontend.ast.List<abs.frontend.ast.Annotation> annotations) {
        writer.println("\tsealed trait Message");
        for (MethodSig s : methods) {
                // run method is special, that will be handled by MyObject.Run
                //if (s.getName().equals("run"))
                //        continue;
  
                writer.format("\tcase %s %s",
                        (s.getNumParam() == 0 ? "object" : "class"),
                        StringUtils.capitalize(s.getName()));
                
                if (s.getNumParam() > 0) {
                        boolean f = false;
                        
                        writer.write("(");
                        
                        for (ParamDecl param: s.getParams()) {
                                if (f)
                                        writer.write(", ");
                                writer.write(param.getName() + ": "); param.getAccess().generateScala("", writer, annotations);                                  
                                f = true;
                        }
                        
                        writer.write(")");
                }
                
                writer.println(" extends Message");
        }
    }
    /*
    public static final String mangleTypeName(TypeDecl t) {
        if (t.getModule().getName().equals("ABS.StdLib")) {
            if (t.getName().equals("Unit") || t.getName().equals("Int") ||
                    t.getName().equals("String") || t.getName().equals("Bool") ||
                    t.getName().equals("Fut"))
                    return;

    }*/
}
