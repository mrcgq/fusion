grammar PostProcessing;

line: (text | variable | functionCall)* EOF;

functionCall: '$' identifier '(' (parameter (',' parameter)*)? ')';

text: CH+?;
identifier: CH+?;

parameter: expression*;

variable: '${' identifier '}';

expression
    : text
    | variable
    | functionCall
    ;

CH: .;
WS: [ \t\r\n]+ -> skip;
