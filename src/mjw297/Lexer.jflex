package mjw297;

import java_cup.runtime.*;

%%

%cupsym Sym
%cup

%class Lexer
%unicode
%line
%column

%state STRING CHARACTER

%{
    StringBuffer sb = new StringBuffer();

    private Symbol symbol(int type) {
        return new Symbol(type, yyline + 1, yycolumn + 1);
    }

    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline + 1, yycolumn + 1, value);
    }
%}




%%

<YYINITIAL> {
	
	/* Keywords */
	"while"		{ return symbol(Sym.WHILE);  }
	"if"		{ return symbol(Sym.IF);     }
	"else"		{ return symbol(Sym.ELSE);   }
    "return"	{ return symbol(Sym.RETURN); }
	"int"		{ return symbol(Sym.INT);    }
	"bool"		{ return symbol(Sym.BOOL);	 }
	"use"		{ return symbol(Sym.USE);	 }
	"length"	{ return symbol(Sym.LENGTH); }
	"true"		{ return symbol(Sym.TRUE);	 }
	"false"		{ return symbol(Sym.FALSE);	 } 

	/* Symbols */
    "-"			{ return symbol(Sym.MINUS);      } 
    "!"			{ return symbol(Sym.BANG);       } 
    "*"			{ return symbol(Sym.STAR);       }
    "*>>"		{ return symbol(Sym.HIGHMULT);   }           
    "/"			{ return symbol(Sym.DIV);        }           
    "%"			{ return symbol(Sym.MOD);        }           
    "+"			{ return symbol(Sym.PLUS);       }           
    "="			{ return symbol(Sym.EQ);         }           
    "<"			{ return symbol(Sym.LT);         }           
    "<="		{ return symbol(Sym.LTE);        }           
    ">="		{ return symbol(Sym.GTE);        }           
    ">"			{ return symbol(Sym.GT);         }           
    "=="		{ return symbol(Sym.EQEQ);       }           
    "!="		{ return symbol(Sym.NEQ);        }           
    "&"			{ return symbol(Sym.AMP);        }           
    "|"			{ return symbol(Sym.BAR);        }           
    ";"			{ return symbol(Sym.SEMICOLON);  }           
    "("			{ return symbol(Sym.LPAREN);     }           
    ")"			{ return symbol(Sym.RPAREN);     }           
    "["			{ return symbol(Sym.LBRACKET);   }           
    "]"			{ return symbol(Sym.RBRACKET);   }           
    "{"			{ return symbol(Sym.LBRACE);     }           
    "}"			{ return symbol(Sym.RBRACE);     }           
    "_"			{ return symbol(Sym.UNDERSCORE); }   
    ","			{ return symbol(Sym.COMMA);      }
    ":"			{ return symbol(Sym.COLON);      }           
                
	/* String */
	\"			{ sb.setLength(0); yybegin(STRING); }         

	/* Character */
	\'			{ sb.setLength(0); yybegin(CHARACTER); }

	/* whitespace */
}               
               
<STRING> {
	/* End of string */
	\"			 { yybegin(YYINITIAL);
				   return symbol(Sym.STRING,
				   sb.toString());}
	[^\n\r\"\\]+ { sb.append( yytext() ); }

	/* escape characters */	
	\\t			 { sb.append('\t');		  }
	\\n			 { sb.append('\n');		  }
	\\r			 { sb.append('\r');		  }
	\\\"		 { sb.append('\"');		  }
	\\			 { sb.append('\\');		  }

} 

<CHARACTER> {
	/* end of character */
	\'			{ yybegin(YYINITIAL);
				  return symbol(Sym.CHAR,
				  sb.toString());}
}

                
[^] { throw new Error("Illegal character <"+ yytext()+">"); }
