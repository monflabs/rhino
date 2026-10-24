/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.monflabs.mozilla.javascript.ast.AbstractObjectProperty;
import org.monflabs.mozilla.javascript.ast.ArrayComprehension;
import org.monflabs.mozilla.javascript.ast.ArrayComprehensionLoop;
import org.monflabs.mozilla.javascript.ast.ArrayLiteral;
import org.monflabs.mozilla.javascript.ast.Assignment;
import org.monflabs.mozilla.javascript.ast.AstNode;
import org.monflabs.mozilla.javascript.ast.AstRoot;
import org.monflabs.mozilla.javascript.ast.BigIntLiteral;
import org.monflabs.mozilla.javascript.ast.Block;
import org.monflabs.mozilla.javascript.ast.BreakStatement;
import org.monflabs.mozilla.javascript.ast.CatchClause;
import org.monflabs.mozilla.javascript.ast.ClassFieldKey;
import org.monflabs.mozilla.javascript.ast.ClassNode;
import org.monflabs.mozilla.javascript.ast.Comment;
import org.monflabs.mozilla.javascript.ast.ComputedPropertyKey;
import org.monflabs.mozilla.javascript.ast.ConditionalExpression;
import org.monflabs.mozilla.javascript.ast.ContinueStatement;
import org.monflabs.mozilla.javascript.ast.DestructuringForm;
import org.monflabs.mozilla.javascript.ast.DoLoop;
import org.monflabs.mozilla.javascript.ast.ElementGet;
import org.monflabs.mozilla.javascript.ast.EmptyExpression;
import org.monflabs.mozilla.javascript.ast.EmptyStatement;
import org.monflabs.mozilla.javascript.ast.ErrorNode;
import org.monflabs.mozilla.javascript.ast.ExpressionStatement;
import org.monflabs.mozilla.javascript.ast.ForInLoop;
import org.monflabs.mozilla.javascript.ast.ForLoop;
import org.monflabs.mozilla.javascript.ast.FunctionCall;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.GeneratorExpression;
import org.monflabs.mozilla.javascript.ast.GeneratorExpressionLoop;
import org.monflabs.mozilla.javascript.ast.GeneratorMethodDefinition;
import org.monflabs.mozilla.javascript.ast.IdeErrorReporter;
import org.monflabs.mozilla.javascript.ast.IfStatement;
import org.monflabs.mozilla.javascript.ast.ImportCall;
import org.monflabs.mozilla.javascript.ast.InfixExpression;
import org.monflabs.mozilla.javascript.ast.Jump;
import org.monflabs.mozilla.javascript.ast.KeywordLiteral;
import org.monflabs.mozilla.javascript.ast.Label;
import org.monflabs.mozilla.javascript.ast.LabeledStatement;
import org.monflabs.mozilla.javascript.ast.LetNode;
import org.monflabs.mozilla.javascript.ast.Loop;
import org.monflabs.mozilla.javascript.ast.Name;
import org.monflabs.mozilla.javascript.ast.NewExpression;
import org.monflabs.mozilla.javascript.ast.NumberLiteral;
import org.monflabs.mozilla.javascript.ast.ObjectLiteral;
import org.monflabs.mozilla.javascript.ast.ObjectProperty;
import org.monflabs.mozilla.javascript.ast.ParenthesizedExpression;
import org.monflabs.mozilla.javascript.ast.PropertyGet;
import org.monflabs.mozilla.javascript.ast.RegExpLiteral;
import org.monflabs.mozilla.javascript.ast.ReturnStatement;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.ast.Spread;
import org.monflabs.mozilla.javascript.ast.SpreadObjectProperty;
import org.monflabs.mozilla.javascript.ast.StringLiteral;
import org.monflabs.mozilla.javascript.ast.SwitchCase;
import org.monflabs.mozilla.javascript.ast.SwitchStatement;
import org.monflabs.mozilla.javascript.ast.Symbol;
import org.monflabs.mozilla.javascript.ast.TaggedTemplateLiteral;
import org.monflabs.mozilla.javascript.ast.TemplateCharacters;
import org.monflabs.mozilla.javascript.ast.TemplateLiteral;
import org.monflabs.mozilla.javascript.ast.ThrowStatement;
import org.monflabs.mozilla.javascript.ast.TryStatement;
import org.monflabs.mozilla.javascript.ast.UnaryExpression;
import org.monflabs.mozilla.javascript.ast.UpdateExpression;
import org.monflabs.mozilla.javascript.ast.VariableDeclaration;
import org.monflabs.mozilla.javascript.ast.VariableInitializer;
import org.monflabs.mozilla.javascript.ast.WhileLoop;
import org.monflabs.mozilla.javascript.ast.WithStatement;
import org.monflabs.mozilla.javascript.ast.XmlDotQuery;
import org.monflabs.mozilla.javascript.ast.XmlElemRef;
import org.monflabs.mozilla.javascript.ast.XmlExpression;
import org.monflabs.mozilla.javascript.ast.XmlLiteral;
import org.monflabs.mozilla.javascript.ast.XmlMemberGet;
import org.monflabs.mozilla.javascript.ast.XmlPropRef;
import org.monflabs.mozilla.javascript.ast.XmlRef;
import org.monflabs.mozilla.javascript.ast.XmlString;
import org.monflabs.mozilla.javascript.ast.Yield;
import org.monflabs.mozilla.javascript.sourcemap.Position;
import org.monflabs.mozilla.javascript.sourcemap.SourceMapper;

/**
 * This class implements the JavaScript parser.
 *
 * <p>It is based on the SpiderMonkey C source files jsparse.c and jsparse.h in the jsref package.
 *
 * <p>The parser generates an {@link AstRoot} parse tree representing the source code. No tree
 * rewriting is permitted at this stage, so that the parse tree is a faithful representation of the
 * source for frontend processing tools and IDEs.
 *
 * <p>This parser implementation is not intended to be reused after a parse finishes, and will throw
 * an IllegalStateException() if invoked again.
 *
 * @see TokenStream
 * @author Mike McCabe
 * @author Brendan Eich
 */
public class Parser {
    /** Maximum number of allowed function or constructor arguments, to follow SpiderMonkey. */
    public static final int ARGC_LIMIT = 1 << 16;

    // TokenInformation flags : currentFlaggedToken stores them together
    // with token type
    static final int CLEAR_TI_MASK = 0xFFFF, // mask to clear token information bits
            TI_AFTER_EOL = 1 << 16, // first token of the source line
            TI_CHECK_LABEL = 1 << 17; // indicates to check for label

    CompilerEnvirons compilerEnv;
    private ErrorReporter errorReporter;
    private IdeErrorReporter errorCollector;
    private String sourceURI;
    private char[] sourceChars;

    boolean calledByCompileFunction; // ugly - set directly by Context
    private boolean parseFinished; // set when finished to prevent reuse

    private TokenStream ts;
    CurrentPositionReporter currentPos;
    private int currentFlaggedToken = Token.EOF;
    private int currentToken;
    private int syntaxErrorCount;

    private List<Comment> scannedComments;
    private Comment currentJsDocComment;

    protected int nestingOfFunction;
    private int nonArrowFunctionDepth;
    protected int nestingOfFunctionParams;
    private FunctionNode functionOfParams;
    private LabeledStatement currentLabel;
    private boolean inDestructuringAssignment;

    /**
     * Inside a parenthesized expression, which may turn out to be arrow parameters: an object
     * literal with a CoverInitializedName ({x = 1}) is only an error if no => follows.
     */
    private boolean inParenthesizedCover;

    private int pendingCoverInitializedNames;

    /** The next statement is the body of a with or labeled statement (no declaration allowed). */
    private boolean letInSingleStatement;

    protected boolean inUseStrictDirective;

    /** The imports and exports of a module, when parsing with the module goal. */
    ModuleInfo moduleInfo;

    private boolean legacyEscapeInPrologue;

    // The following are per function variables and should be saved/restored
    // during function parsing.  See PerFunctionVariables class below.
    ScriptNode currentScriptOrFn;
    private boolean insideMethod;
    private boolean inClassHeritage;
    Scope currentScope;
    private int endFlags;
    private boolean inForInit; // bound temporarily during forStatement()
    private boolean
            inSingleStatementContext; // true when parsing a single-statement body (if/while/for
    private boolean inSingleStatementDeclContext; // true when parsing a
    // single-statement body
    // that allows lexical
    // declarations.  without
    // braces)
    private Map<String, LabeledStatement> labelSet;
    private List<Loop> loopSet;
    private List<Jump> loopAndSwitchSet;
    private boolean hasUndefinedBeenRedefined = false;
    // end of per function variables

    // Lacking 2-token lookahead, labels become a problem.
    // These vars store the token info of the last matched name,
    // iff it wasn't the last matched token.
    private int prevNameTokenStart;
    private String prevNameTokenString = "";
    private int prevNameTokenLineno;
    private int prevNameTokenColumn;
    private int lastTokenLineno = -1;
    private int lastTokenColumn = -1;

    // Exception to unwind
    public static class ParserException extends RuntimeException {
        private static final long serialVersionUID = 5882582646773765630L;
    }

    static interface Transformer {
        Node transform(AstNode node);
    }

    public Parser() {
        this(new CompilerEnvirons());
    }

    public Parser(CompilerEnvirons compilerEnv) {
        this(compilerEnv, compilerEnv.getErrorReporter());
    }

    public Parser(CompilerEnvirons compilerEnv, ErrorReporter errorReporter) {
        this.compilerEnv = compilerEnv;
        this.errorReporter = errorReporter;
        if (errorReporter instanceof IdeErrorReporter) {
            errorCollector = (IdeErrorReporter) errorReporter;
        }
    }

    // Add a strict warning on the last matched token.
    void addStrictWarning(String messageId, String messageArg) {
        addStrictWarning(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void addStrictWarning(String messageId, String messageArg, int position, int length) {
        if (compilerEnv.isStrictMode()) addWarning(messageId, messageArg, position, length);
    }

    void addWarning(String messageId, String messageArg) {
        addWarning(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void addWarning(String messageId, int position, int length) {
        addWarning(messageId, null, position, length);
    }

    void addWarning(String messageId, String messageArg, int position, int length) {
        String message = lookupMessage(messageId, messageArg);
        if (compilerEnv.reportWarningAsError()) {
            addError(messageId, messageArg, position, length);
        } else if (errorCollector != null) {
            errorCollector.warning(message, sourceURI, position, length);
        } else {
            MappedLocation loc = mapCurrentPos();
            errorReporter.warning(message, sourceURI, loc.line, loc.lineSource, loc.offset);
        }
    }

    void addError(String messageId) {
        addError(messageId, currentPos.getPosition(), currentPos.getLength());
    }

    void addError(String messageId, int position, int length) {
        addError(messageId, null, position, length);
    }

    void addError(String messageId, String messageArg) {
        addError(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void addError(String messageId, int c) {
        String messageArg = Character.toString((char) c);
        addError(messageId, messageArg);
    }

    void addError(String messageId, String messageArg, int position, int length) {
        ++syntaxErrorCount;
        String message = lookupMessage(messageId, messageArg);
        if (errorCollector != null) {
            errorCollector.error(message, sourceURI, position, length);
        } else {
            MappedLocation loc = mapCurrentPos();
            errorReporter.error(message, sourceURI, loc.line, loc.lineSource, loc.offset);
        }
    }

    private static final class MappedLocation {
        private final int line;
        private final String lineSource;
        private final int offset;

        private MappedLocation(int line, String lineSource, int offset) {
            this.line = line;
            this.lineSource = lineSource;
            this.offset = offset;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (MappedLocation) obj;
            return this.line == that.line
                    && Objects.equals(this.lineSource, that.lineSource)
                    && this.offset == that.offset;
        }

        @Override
        public int hashCode() {
            return Objects.hash(line, lineSource, offset);
        }

        @Override
        public String toString() {
            return "MappedLocation["
                    + "line="
                    + line
                    + ", "
                    + "lineSource="
                    + lineSource
                    + ", "
                    + "offset="
                    + offset
                    + ']';
        }
    }

    private MappedLocation mapCurrentPos() {
        return mapLocation(currentPos.getLineno(), currentPos.getLine(), currentPos.getOffset());
    }

    private void addStrictWarning(
            String messageId,
            String messageArg,
            int position,
            int length,
            int line,
            String lineSource,
            int lineOffset) {
        if (compilerEnv.isStrictMode()) {
            addWarning(messageId, messageArg, position, length, line, lineSource, lineOffset);
        }
    }

    private void addWarning(
            String messageId,
            String messageArg,
            int position,
            int length,
            int line,
            String lineSource,
            int lineOffset) {
        String message = lookupMessage(messageId, messageArg);
        if (compilerEnv.reportWarningAsError()) {
            addError(messageId, messageArg, position, length, line, lineSource, lineOffset);
        } else if (errorCollector != null) {
            errorCollector.warning(message, sourceURI, position, length);
        } else {
            MappedLocation loc = mapLocation(line, lineSource, lineOffset);
            errorReporter.warning(message, sourceURI, loc.line, loc.lineSource, loc.offset);
        }
    }

    private void addError(
            String messageId,
            String messageArg,
            int position,
            int length,
            int line,
            String lineSource,
            int lineOffset) {
        ++syntaxErrorCount;
        String message = lookupMessage(messageId, messageArg);
        if (errorCollector != null) {
            errorCollector.error(message, sourceURI, position, length);
        } else {
            MappedLocation loc = mapLocation(line, lineSource, lineOffset);
            errorReporter.error(message, sourceURI, loc.line, loc.lineSource, loc.offset);
        }
    }

    private MappedLocation mapLocation(int line, String lineSource, int offset) {
        SourceMapper mapper = compilerEnv.getSourceMapper();
        if (mapper != null) {
            Position mapped = mapper.mapPosition(line, offset);
            if (mapped != null) {
                line = mapped.getLine();
                offset = mapped.getColumn();
                String mappedLine = mapper.getSourceLineText(mapped.getSourcePath(), line);
                if (mappedLine != null) {
                    lineSource = mappedLine;
                }
            }
        }
        return new MappedLocation(line, lineSource, offset);
    }

    String lookupMessage(String messageId) {
        return lookupMessage(messageId, null);
    }

    String lookupMessage(String messageId, String messageArg) {
        return messageArg == null
                ? ScriptRuntime.getMessageById(messageId)
                : ScriptRuntime.getMessageById(messageId, messageArg);
    }

    void reportError(String messageId) {
        reportError(messageId, null);
    }

    void reportError(String messageId, String messageArg) {
        reportError(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void reportError(String messageId, int position, int length) {
        reportError(messageId, null, position, length);
    }

    void reportError(String messageId, String messageArg, int position, int length) {
        addError(messageId, messageArg, position, length);

        if (!compilerEnv.recoverFromErrors()) {
            throw new ParserException();
        }
    }

    // Computes the absolute end offset of node N.
    // Use with caution!  Assumes n.getPosition() is -absolute-, which
    // is only true before the node is added to its parent.
    private static int getNodeEnd(AstNode n) {
        return n.getPosition() + n.getLength();
    }

    private void recordComment(int lineno, int column, String comment) {
        if (scannedComments == null) {
            scannedComments = new ArrayList<>();
        }
        Comment commentNode =
                new Comment(ts.tokenBeg, ts.getTokenLength(), ts.commentType, comment);
        if (ts.commentType == Token.CommentType.JSDOC
                && compilerEnv.isRecordingLocalJsDocComments()) {
            currentJsDocComment =
                    new Comment(ts.tokenBeg, ts.getTokenLength(), ts.commentType, comment);
            currentJsDocComment.setLineColumnNumber(lineno, column);
        }
        commentNode.setLineColumnNumber(lineno, column);
        scannedComments.add(commentNode);
    }

    private Comment getAndResetJsDoc() {
        Comment saved = currentJsDocComment;
        currentJsDocComment = null;
        return saved;
    }

    // Returns the next token without consuming it.
    // If previous token was consumed, calls scanner to get new token.
    // If previous token was -not- consumed, returns it (idempotent).
    //
    // This function will not return a newline (Token.EOL - instead, it
    // gobbles newlines until it finds a non-newline token, and flags
    // that token as appearing just after a newline.
    //
    // This function will also not return a Token.COMMENT.  Instead, it
    // records comments in the scannedComments list.  If the token
    // returned by this function immediately follows a jsdoc comment,
    // the token is flagged as such.
    //
    // Note that this function always returned the un-flagged token!
    // The flags, if any, are saved in currentFlaggedToken.
    private int peekToken() throws IOException {
        // By far the most common case:  last token hasn't been consumed,
        // so return already-peeked token.
        if (currentFlaggedToken != Token.EOF) {
            return currentToken;
        }

        int tt = ts.getToken();
        boolean sawEOL = false;

        // process comments and whitespace
        while (tt == Token.EOL || tt == Token.COMMENT) {
            if (tt == Token.EOL) {
                sawEOL = true;
                tt = ts.getToken();
            } else {
                // a multi-line comment with a line terminator is a LineTerminator (12.4)
                if (ts.commentHasLineTerminator) {
                    ts.commentHasLineTerminator = false;
                    if (compilerEnv.isLatestEcmaScript()) {
                        sawEOL = true;
                    }
                }
                if (compilerEnv.isRecordingComments()) {
                    String comment = ts.getAndResetCurrentComment();
                    recordComment(ts.getTokenStartLineno(), ts.getTokenColumn(), comment);
                    break;
                }
                tt = ts.getToken();
            }
        }

        if (tt == Token.YIELD && isYieldAnIdentifier()) {
            tt = Token.NAME;
        } else if (tt == Token.AWAIT && !compilerEnv.isLatestEcmaScript()) {
            // await is a reserved word before VERSION_ECMASCRIPT
            tt = Token.RESERVED;
        } else if (tt == Token.AWAIT && isAwaitAnIdentifier()) {
            tt = Token.NAME;
        }
        currentToken = tt;
        currentFlaggedToken = tt | (sawEOL ? TI_AFTER_EOL : 0);
        return currentToken; // return unflagged token
    }

    /**
     * yield is an identifier in non-strict code outside generator functions (ECMA-262 13.1.1); in
     * versions before ES6, a yield makes the function a (legacy) generator.
     */
    private boolean isYieldAnIdentifier() {
        return compilerEnv.isLatestEcmaScript()
                && !inUseStrictDirective
                && !(currentScriptOrFn instanceof FunctionNode
                        && ((FunctionNode) currentScriptOrFn).isGeneratorFunction());
    }

    /**
     * await is an identifier outside async functions in scripts (ECMA-262 13.1.1); it is a keyword
     * in the body and the parameters of an async function, and anywhere in a module.
     */
    private boolean isAwaitAnIdentifier() {
        return moduleInfo == null && !inAsyncFunction() && !inStaticBlock;
    }

    /**
     * The destructuring parameters of a function, desugared in source order under
     * VERSION_ECMASCRIPT (in hash order before).
     */
    private Map<String, Node> newDestructuringMap() {
        return compilerEnv.isLatestEcmaScript() ? new LinkedHashMap<>() : new HashMap<>();
    }

    /** Whether the code being parsed is the top level of a module, where await is allowed. */
    private boolean isModuleTopLevel() {
        return moduleInfo != null && !(currentScriptOrFn instanceof FunctionNode);
    }

    private boolean inAsyncGenerator() {
        return currentScriptOrFn instanceof FunctionNode
                && ((FunctionNode) currentScriptOrFn).isAsyncGenerator();
    }

    private boolean inAsyncFunction() {
        return currentScriptOrFn instanceof FunctionNode
                && ((FunctionNode) currentScriptOrFn).isAsync();
    }

    /**
     * Whether the peeked NAME token is the async of an async function declaration or expression:
     * async, then function on the same line.
     */
    private boolean isAsyncFunctionStart() {
        if (currentToken != Token.NAME
                || ts.nameHadEscape
                || !"async".equals(ts.getString())
                || !compilerEnv.isLatestEcmaScript()) {
            return false;
        }
        TokenStream.LookAhead next = ts.lookAhead();
        return next != null && !next.lineBreak && "function".equals(next.word);
    }

    /** Consumes async and function, and parses an async function. */
    private FunctionNode asyncFunction(int type) throws IOException {
        consumeToken();
        int asyncStart = ts.tokenBeg;
        mustMatchToken(Token.FUNCTION, "msg.syntax", true);
        functionIsAsync = true;
        FunctionNode fn = function(type);
        fn.setRawSourceBounds(asyncStart, fn.getRawSourceEnd());
        return fn;
    }

    /** Set before function() parses an async function or method. */
    private boolean functionIsAsync;

    /**
     * The async name just parsed before a ( on the same line: the call it starts is the cover of
     * the parameters of an async arrow function when => follows (ECMA-262 15.9).
     */
    private AstNode asyncCallTarget;

    /** Whether the peeked NAME is the async of async x => ...: an identifier on the same line. */
    private boolean isAsyncArrowWithIdentifierStart() {
        if (currentToken != Token.NAME
                || ts.nameHadEscape
                || !"async".equals(ts.getString())
                || !compilerEnv.isLatestEcmaScript()) {
            return false;
        }
        TokenStream.LookAhead next = ts.lookAhead();
        return next != null
                && !next.lineBreak
                && next.startsIdentifier()
                && !"function".equals(next.word)
                && !"in".equals(next.word)
                && !"instanceof".equals(next.word)
                // for await (async of ...): async is the target of the loop
                && !(inForInit && "of".equals(next.word) && !next.followedByArrow);
    }

    private AstNode asyncArrowWithIdentifier() throws IOException {
        consumeToken();
        int asyncStart = ts.tokenBeg;
        int startLine = lineNumber(), startColumn = columnNumber();
        if (peekToken() != Token.NAME) {
            if (currentToken == Token.AWAIT) {
                reportError("msg.bad.await");
            }
            reportError("msg.syntax");
        }
        consumeToken();
        AstNode param = createNameNode(true, Token.NAME);
        if (peekTokenOrEOL() != Token.ARROW) {
            reportError("msg.syntax");
        }
        consumeToken();
        functionIsAsync = true;
        return arrowFunction(param, startLine, startColumn, asyncStart);
    }

    /**
     * The parameters of an arrow function cannot contain an await expression (ECMA-262 15.3.1),
     * nor, for an async arrow function, await as an identifier (15.9.1).
     */
    private void checkAwaitInArrowParams(AstNode params, boolean isAsync) {
        params.visit(
                node -> {
                    if (node instanceof FunctionNode
                            && ((FunctionNode) node).getFunctionType()
                                    != FunctionNode.ARROW_FUNCTION) {
                        return false;
                    }
                    if ((node instanceof Yield && ((Yield) node).isAwait())
                            || (isAsync
                                    && node instanceof Name
                                    && "await".equals(((Name) node).getIdentifier()))) {
                        reportError("msg.bad.await");
                    }
                    return true;
                });
    }

    /**
     * With parameter expressions, the body of a function has its own var environment, which the
     * closures created by the parameters do not see (FunctionDeclarationInstantiation, step 28):
     * the var bindings of the body that such a closure names are renamed, so that the closure
     * resolves the name outside the function. Not done when the body may look names up dynamically
     * (eval, with).
     */
    private void separateBodyVars(FunctionNode fnNode) {
        if (!compilerEnv.isLatestEcmaScript() || fnNode.getSymbolTable() == null) {
            return;
        }
        List<AstNode> paramExpressions = new ArrayList<>();
        if (fnNode.getDefaultParams() != null) {
            for (Object o : fnNode.getDefaultParams()) {
                if (o instanceof AstNode) {
                    paramExpressions.add((AstNode) o);
                }
            }
        }
        for (AstNode param : fnNode.getParams()) {
            if (!(param instanceof Name)) {
                paramExpressions.add(param);
            }
        }
        Set<String> closureNames = new HashSet<>();
        Set<String> paramNames = new HashSet<>();
        for (AstNode expression : paramExpressions) {
            expression.visit(
                    node -> {
                        if (node instanceof Name) {
                            paramNames.add(((Name) node).getIdentifier());
                        }
                        if (node instanceof FunctionNode) {
                            node.visit(
                                    inner -> {
                                        if (inner instanceof Name) {
                                            closureNames.add(((Name) inner).getIdentifier());
                                        }
                                        return true;
                                    });
                            return false;
                        }
                        return true;
                    });
        }
        // an arrow function has no arguments object: its body's arguments is a plain binding
        boolean arrow = fnNode.getFunctionType() == FunctionNode.ARROW_FUNCTION;
        Set<String> renamed = new HashSet<>();
        Set<String> functionDecls = new HashSet<>();
        for (Symbol symbol : fnNode.getSymbolTable().values()) {
            if (symbol.getDeclType() == Symbol.Type.VAR
                    && closureNames.contains(symbol.getName())
                    && (arrow || !"arguments".equals(symbol.getName()))) {
                renamed.add(symbol.getName());
            } else if (symbol.getDeclType() == Symbol.Type.FUNCTION_VAR
                    && arrow
                    && (paramNames.contains(symbol.getName())
                            || closureNames.contains(symbol.getName()))) {
                // a function declaration of the body, bound in its var environment
                renamed.add(symbol.getName());
                functionDecls.add(symbol.getName());
            } else if ((symbol.getDeclType() == Symbol.Type.LET
                            || symbol.getDeclType() == Symbol.Type.CONST)
                    && (paramNames.contains(symbol.getName())
                            || closureNames.contains(symbol.getName()))) {
                // the lexical declarations of the body are not visible to the parameters either
                renamed.add(symbol.getName());
            }
        }
        if (renamed.isEmpty() || mayLookUpNamesDynamically(fnNode.getBody())) {
            return;
        }
        List<Name> uses = new ArrayList<>();
        fnNode.getBody()
                .visit(
                        node -> {
                            if (node instanceof Name
                                    && renamed.contains(((Name) node).getIdentifier())
                                    && !isPropertyName((Name) node)
                                    && !isFunctionName((Name) node)
                                    && !inOwnArgumentsScope((Name) node, fnNode)
                                    && definingScope((Name) node) == fnNode) {
                                uses.add((Name) node);
                            }
                            return true;
                        });
        for (String name : renamed) {
            Symbol symbol = fnNode.getSymbolTable().remove(name);
            symbol.setName(name + "$body");
            fnNode.getSymbolTable().put(symbol.getName(), symbol);
        }
        for (Name use : uses) {
            use.setIdentifier(use.getIdentifier() + "$body");
        }
        if (!functionDecls.isEmpty()) {
            hoistFunctionDeclarations(fnNode, functionDecls);
        }
    }

    /** The name of a function, rather than a reference to a binding. */
    private static boolean isFunctionName(Name name) {
        return name.getParent() instanceof FunctionNode
                && ((FunctionNode) name.getParent()).getFunctionName() == name;
    }

    /** An arguments inside a non-arrow function nested in fnNode, its own arguments object. */
    private static boolean inOwnArgumentsScope(Name name, FunctionNode fnNode) {
        if (!"arguments".equals(name.getIdentifier())) {
            return false;
        }
        for (AstNode node = name.getParent(); node != fnNode; node = node.getParent()) {
            if (node instanceof FunctionNode
                    && ((FunctionNode) node).getFunctionType() != FunctionNode.ARROW_FUNCTION) {
                return true;
            }
        }
        return false;
    }

    /**
     * The function declarations of the body that separateBodyVars renamed become assignments of
     * function expressions to the renamed bindings, at the start of the body: the function keeps
     * its name, and is bound in the var environment of the body only.
     */
    private static void hoistFunctionDeclarations(FunctionNode fnNode, Set<String> names) {
        AstNode body = fnNode.getBody();
        List<AstNode> declarations = new ArrayList<>();
        for (Node n = body.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof FunctionNode
                    && ((FunctionNode) n).getFunctionType() == FunctionNode.FUNCTION_STATEMENT
                    && names.contains(((FunctionNode) n).getName())) {
                declarations.add((AstNode) n);
            }
        }
        for (int i = declarations.size() - 1; i >= 0; i--) {
            FunctionNode fn = (FunctionNode) declarations.get(i);
            body.removeChild(fn);
            fn.setFunctionType(FunctionNode.FUNCTION_EXPRESSION);
            Name target = new Name(fn.getPosition(), fn.getName() + "$body");
            Assignment assignment = new Assignment(Token.ASSIGN, target, fn, fn.getPosition());
            ExpressionStatement statement = new ExpressionStatement(assignment, false);
            statement.setLineColumnNumber(fn.getLineno(), fn.getColumn());
            body.addChildToFront(statement);
            statement.setParent(body);
        }
    }

    /**
     * Renames the let and const bindings of a block scope that code evaluated outside of the block
     * names (the discriminant of a switch, the parameter of a catch): the uses inside the block
     * follow the new name, and the outside code resolves the name in the enclosing scopes.
     */
    private void hideBlockBindings(Scope scope, AstNode outside, Collection<String> keep) {
        if (!compilerEnv.isLatestEcmaScript()
                || outside == null
                || scope.getSymbolTable() == null
                || mayLookUpNamesDynamically(scope)) {
            return;
        }
        Set<String> outsideNames = new HashSet<>();
        outside.visit(
                node -> {
                    if (node instanceof Name) {
                        outsideNames.add(((Name) node).getIdentifier());
                    }
                    return true;
                });
        Set<String> renamed = new HashSet<>();
        for (Symbol symbol : scope.getSymbolTable().values()) {
            if ((symbol.getDeclType() == Symbol.Type.LET
                            || symbol.getDeclType() == Symbol.Type.CONST)
                    && outsideNames.contains(symbol.getName())
                    && !keep.contains(symbol.getName())) {
                renamed.add(symbol.getName());
            }
        }
        if (renamed.isEmpty()) {
            return;
        }
        Set<AstNode> outsideNodes = new HashSet<>();
        outside.visit(
                node -> {
                    outsideNodes.add(node);
                    return true;
                });
        List<Name> uses = new ArrayList<>();
        scope.visit(
                node -> {
                    if (outsideNodes.contains(node)) {
                        return false;
                    }
                    if (node instanceof Name
                            && renamed.contains(((Name) node).getIdentifier())
                            && !isPropertyName((Name) node)
                            && definingScope((Name) node) == scope) {
                        uses.add((Name) node);
                    }
                    return true;
                });
        for (String name : renamed) {
            Symbol symbol = scope.getSymbolTable().remove(name);
            symbol.setName(name + "$block");
            scope.getSymbolTable().put(symbol.getName(), symbol);
        }
        for (Name use : uses) {
            use.setIdentifier(use.getIdentifier() + "$block");
        }
    }

    /**
     * The expression of a for-in/of head with let or const is evaluated where the bindings of the
     * loop exist but are not initialized (ForIn/OfHeadEvaluation, TDZnames): a closure created
     * there sees them in their temporal dead zone. Its references to them are renamed to bindings
     * of a block around the loop that are never initialized.
     */
    private AstNode hideLoopHeadBindings(Loop loop) {
        if (!(loop instanceof ForInLoop)
                || !compilerEnv.isLatestEcmaScript()
                || !(((ForInLoop) loop).getIterator() instanceof VariableDeclaration)) {
            return loop;
        }
        ForInLoop forIn = (ForInLoop) loop;
        VariableDeclaration decl = (VariableDeclaration) forIn.getIterator();
        if (decl.getType() != Token.LET && decl.getType() != Token.CONST) {
            return loop;
        }
        List<String> names = new ArrayList<>();
        for (VariableInitializer vi : decl.getVariables()) {
            collectBoundNames(vi.getTarget(), names);
        }
        List<Name> uses = new ArrayList<>();
        forIn.getIteratedObject()
                .visit(
                        node -> {
                            if (node instanceof FunctionNode) {
                                node.visit(
                                        inner -> {
                                            if (inner instanceof Name
                                                    && names.contains(
                                                            ((Name) inner).getIdentifier())
                                                    && !isPropertyName((Name) inner)
                                                    && !isWithin(
                                                            definingScope((Name) inner), node)) {
                                                // not shadowed in the closure
                                                uses.add((Name) inner);
                                            }
                                            return true;
                                        });
                                return false;
                            }
                            return true;
                        });
        if (uses.isEmpty()) {
            return loop;
        }
        Scope block = new Scope(loop.getPosition(), loop.getLength());
        block.setLineColumnNumber(loop.getLineno(), loop.getColumn());
        block.setParentScope(loop.getParentScope());
        loop.setParentScope(block);
        block.addChild(loop);
        for (Name use : uses) {
            String tdzName = use.getIdentifier() + "$tdz";
            if (block.getSymbol(tdzName) == null) {
                Symbol symbol = new Symbol(Symbol.Type.LET, tdzName);
                symbol.setNode(use);
                block.putSymbol(symbol);
            }
            use.setIdentifier(tdzName);
        }
        return block;
    }

    private static boolean isWithin(AstNode node, AstNode ancestor) {
        for (AstNode n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** The innermost scope around a name that declares it, by the parents of the AST. */
    private static Scope definingScope(Name name) {
        for (AstNode node = name.getParent(); node != null; node = node.getParent()) {
            if (node instanceof Scope && ((Scope) node).getSymbol(name.getIdentifier()) != null) {
                return (Scope) node;
            }
        }
        return null;
    }

    private static boolean mayLookUpNamesDynamically(AstNode body) {
        boolean[] dynamic = {false};
        body.visit(
                node -> {
                    if (node instanceof WithStatement
                            || (node instanceof FunctionCall
                                    && ((FunctionCall) node).getTarget() instanceof Name
                                    && "eval"
                                            .equals(
                                                    ((Name) ((FunctionCall) node).getTarget())
                                                            .getIdentifier()))) {
                        dynamic[0] = true;
                    }
                    return !dynamic[0];
                });
        return dynamic[0];
    }

    /** A Name that is a property name or a label, not a reference to a binding. */
    private static boolean isPropertyName(Name name) {
        AstNode parent = name.getParent();
        return (parent instanceof PropertyGet && ((PropertyGet) parent).getProperty() == name)
                || (parent instanceof ObjectProperty && ((ObjectProperty) parent).getKey() == name)
                || parent instanceof BreakStatement
                || parent instanceof ContinueStatement
                || parent instanceof Label;
    }

    /** ArrowParameters may not contain a YieldExpression (15.3.1, early errors). */
    private void checkYieldInArrowParams(AstNode params) {
        params.visit(
                node -> {
                    if (node instanceof FunctionNode
                            && ((FunctionNode) node).getFunctionType()
                                    != FunctionNode.ARROW_FUNCTION) {
                        return false;
                    }
                    if (node instanceof Yield && !((Yield) node).isAwait()) {
                        reportError("msg.syntax");
                    }
                    return true;
                });
    }

    /** The parameters of async (...) => ..., from the call that covered them. */
    private AstNode asyncArrowParams(FunctionCall call) {
        int lp = call.getPosition() + call.getLp();
        int rp = call.getPosition() + call.getRp();
        List<AstNode> args = call.getArguments();
        for (AstNode arg : args) {
            checkAwaitInArrowParams(arg, true);
        }
        if (!args.isEmpty()
                && args.get(args.size() - 1) instanceof Spread
                && call.getIntProp(Node.TRAILING_COMMA, 0) == 1) {
            reportError("msg.parm.after.rest");
        }
        AstNode params = null;
        for (AstNode arg : args) {
            params =
                    params == null
                            ? arg
                            : new InfixExpression(Token.COMMA, params, arg, arg.getPosition());
        }
        if (params == null) {
            params = new EmptyExpression(lp + 1, 0);
        }
        ParenthesizedExpression parens = new ParenthesizedExpression(lp, rp + 1 - lp, params);
        if (call.getIntProp(Node.TRAILING_COMMA, 0) == 1) {
            parens.putIntProp(Node.TRAILING_COMMA, 1);
        }
        return parens;
    }

    /**
     * A "use strict" directive also applies to the token peeked after it: a name that is reserved
     * in strict code (public, static, ...) becomes that keyword.
     */
    private void applyStrictToPeekedToken() {
        if (!compilerEnv.isLatestEcmaScript()) {
            return;
        }
        if (currentFlaggedToken != Token.EOF && currentToken == Token.NAME && !ts.nameHadEscape) {
            int strictToken =
                    TokenStream.keywordToken(
                            ts.getString(), compilerEnv.getLanguageVersion(), true);
            if (strictToken != Token.EOF
                    && strictToken != Token.NAME
                    && !(strictToken == Token.AWAIT && isAwaitAnIdentifier())) {
                currentFlaggedToken = strictToken | (currentFlaggedToken & ~CLEAR_TI_MASK);
                currentToken = strictToken;
            }
        }
    }

    private int lineNumber() {
        return lastTokenLineno;
    }

    private int columnNumber() {
        return lastTokenColumn;
    }

    private int peekFlaggedToken() throws IOException {
        peekToken();
        return currentFlaggedToken;
    }

    private void consumeToken() {
        currentFlaggedToken = Token.EOF;
        lastTokenLineno = ts.getTokenStartLineno();
        lastTokenColumn = ts.getTokenColumn();
    }

    private int nextToken() throws IOException {
        int tt = peekToken();
        consumeToken();
        return tt;
    }

    private boolean matchToken(int toMatch, boolean ignoreComment) throws IOException {
        int tt = peekToken();
        while (tt == Token.COMMENT && ignoreComment) {
            consumeToken();
            tt = peekToken();
        }
        if (tt != toMatch) {
            return false;
        }
        consumeToken();
        return true;
    }

    // Returns Token.EOL if the current token follows a newline, else returns
    // the current token.  Used in situations where we don't consider certain
    // token types valid if they are preceded by a newline.  One example is the
    // postfix ++ or -- operator, which has to be on the same line as its
    // operand.
    private int peekTokenOrEOL() throws IOException {
        int tt = peekToken();
        // Check for last peeked token flags
        if ((currentFlaggedToken & TI_AFTER_EOL) != 0) {
            tt = Token.EOL;
        }
        return tt;
    }

    private boolean mustMatchToken(int toMatch, String messageId, boolean ignoreComment)
            throws IOException {
        return mustMatchToken(
                toMatch, messageId, ts.tokenBeg, ts.tokenEnd - ts.tokenBeg, ignoreComment);
    }

    private boolean mustMatchToken(
            int toMatch, String msgId, int pos, int len, boolean ignoreComment) throws IOException {
        if (matchToken(toMatch, ignoreComment)) {
            return true;
        }
        reportError(msgId, pos, len);
        return false;
    }

    private void mustHaveXML() {
        if (!compilerEnv.isXmlAvailable()) {
            reportError("msg.XML.not.available");
        }
    }

    public boolean eof() {
        return ts.eof();
    }

    boolean insideFunctionBody() {
        return nestingOfFunction != 0;
    }

    boolean insideFunctionParams() {
        return nestingOfFunctionParams != 0;
    }

    void pushScope(Scope scope) {
        Scope parent = scope.getParentScope();
        // During codegen, parent scope chain may already be initialized,
        // in which case we just need to set currentScope variable.
        if (parent != null) {
            if (parent != currentScope) codeBug();
        } else {
            currentScope.addChildScope(scope);
        }
        currentScope = scope;
    }

    void popScope() {
        currentScope = currentScope.getParentScope();
    }

    private void enterLoop(Loop loop) {
        if (loopSet == null) loopSet = new ArrayList<>();
        loopSet.add(loop);
        if (loopAndSwitchSet == null) loopAndSwitchSet = new ArrayList<>();
        loopAndSwitchSet.add(loop);
        pushScope(loop);
        if (currentLabel != null) {
            currentLabel.setStatement(loop);
            currentLabel.getFirstLabel().setLoop(loop);
            // This is the only time during parsing that we set a node's parent
            // before parsing the children.  In order for the child node offsets
            // to be correct, we adjust the loop's reported position back to an
            // absolute source offset, and restore it when we call
            // restoreRelativeLoopPosition() (invoked just before setBody() is
            // called on the loop).
            loop.setRelative(-currentLabel.getPosition());
        }
    }

    private void exitLoop() {
        loopSet.remove(loopSet.size() - 1);
        loopAndSwitchSet.remove(loopAndSwitchSet.size() - 1);
        popScope();
    }

    private void restoreRelativeLoopPosition(Loop loop) {
        if (loop.getParent() != null) { // see comment in enterLoop
            loop.setRelative(loop.getParent().getPosition());
        }
    }

    private void enterSwitch(SwitchStatement node) {
        if (loopAndSwitchSet == null) loopAndSwitchSet = new ArrayList<>();
        loopAndSwitchSet.add(node);
    }

    private void exitSwitch() {
        loopAndSwitchSet.remove(loopAndSwitchSet.size() - 1);
    }

    /**
     * Builds a parse tree from the given source string.
     *
     * @return an {@link AstRoot} object representing the parsed program. If the parse fails, {@code
     *     null} will be returned. (The parse failure will result in a call to the {@link
     *     ErrorReporter} from {@link CompilerEnvirons}.)
     */
    public AstRoot parse(String sourceString, String sourceURI, int lineno) {
        if (parseFinished) throw new IllegalStateException("parser reused");
        this.sourceURI = sourceURI;
        if (compilerEnv.isIdeMode()) {
            this.sourceChars = sourceString.toCharArray();
        }
        currentPos = ts = new TokenStream(this, null, sourceString, lineno);
        try {
            return parse();
        } catch (IOException iox) {
            // Should never happen
            throw new IllegalStateException();
        } finally {
            parseFinished = true;
        }
    }

    /**
     * Parses a module (the module goal, ECMA-262 16.2): strict code with top-level import and
     * export declarations, recorded in {@link #moduleInfo}.
     */
    AstRoot parseModule(String sourceString, String sourceURI, int lineno) {
        moduleInfo = new ModuleInfo(sourceString);
        AstRoot root = parse(sourceString, sourceURI, lineno);
        checkModuleExports(root);
        return root;
    }

    /**
     * Builds a parse tree from the given sourcereader.
     *
     * @see #parse(String,String,int)
     * @throws IOException if the {@link Reader} encounters an error
     * @deprecated use parse(String, String, int) instead
     */
    @Deprecated
    public AstRoot parse(Reader sourceReader, String sourceURI, int lineno) throws IOException {
        if (parseFinished) throw new IllegalStateException("parser reused");
        if (compilerEnv.isIdeMode()) {
            return parse(Kit.readReader(sourceReader), sourceURI, lineno);
        }
        try {
            this.sourceURI = sourceURI;
            currentPos = ts = new TokenStream(this, sourceReader, null, lineno);
            return parse();
        } finally {
            parseFinished = true;
        }
    }

    private AstRoot parse() throws IOException {
        int pos = 0;
        AstRoot root = new AstRoot(pos);
        currentScope = currentScriptOrFn = root;

        int baseLineno = ts.lineno; // line number where source starts
        prevNameTokenLineno = ts.getLineno();
        prevNameTokenColumn = ts.getTokenColumn();
        int end = pos; // in case source is empty

        boolean inDirectivePrologue = true;
        legacyEscapeInPrologue = false;
        boolean savedStrictMode = inUseStrictDirective;

        inUseStrictDirective = compilerEnv.isStrictMode() || moduleInfo != null;
        if (inUseStrictDirective) {
            root.setInStrictMode(true);
        }
        inFieldInitializer = compilerEnv.isInEval() && compilerEnv.isEvalInFieldInitializer();

        try {
            for (; ; ) {
                int tt = peekToken();
                if (tt <= Token.EOF) {
                    break;
                }

                AstNode n;
                boolean isAsync = tt == Token.NAME && isAsyncFunctionStart();
                if (tt == Token.FUNCTION || isAsync) {
                    if (!isAsync) {
                        consumeToken();
                    }
                    try {
                        int type =
                                calledByCompileFunction
                                        ? FunctionNode.FUNCTION_EXPRESSION
                                        : FunctionNode.FUNCTION_STATEMENT;
                        n = isAsync ? asyncFunction(type) : function(type);
                        if (calledByCompileFunction) {
                            ((FunctionNode) n).setCompiledByContext();
                        }
                    } catch (ParserException e) {
                        break;
                    }
                } else if (tt == Token.COMMENT) {
                    n = scannedComments.get(scannedComments.size() - 1);
                    consumeToken();
                } else if (moduleInfo != null
                        && tt == Token.RESERVED
                        && ("import".equals(ts.getString()) || "export".equals(ts.getString()))
                        && !isImportExpressionStart()) {
                    n = moduleItem();
                    inDirectivePrologue = false;
                } else {
                    n = statement();
                    if (inDirectivePrologue) {
                        String directive = getDirective(n);
                        if (directive == null) {
                            inDirectivePrologue = false;
                        } else if ("use strict".equals(directive)) {
                            checkLegacyEscapeInPrologue();
                            inUseStrictDirective = true;
                            applyStrictToPeekedToken();
                            root.setInStrictMode(true);
                        }
                    }
                }
                end = getNodeEnd(n);
                root.addChildToBack(n);
                n.setParent(root);
            }
        } catch (StackOverflowError ex) {
            String msg = lookupMessage("msg.too.deep.parser.recursion");
            if (!compilerEnv.isIdeMode())
                throw Context.reportRuntimeError(msg, sourceURI, lineNumber(), null, 0);
        } finally {
            inUseStrictDirective = savedStrictMode;
        }

        Predicate<String> forbidden = compilerEnv.getEvalForbiddenVarNames();
        if (forbidden != null && !root.isInStrictMode() && root.getSymbolTable() != null) {
            for (Symbol symbol : root.getSymbolTable().values()) {
                Symbol.Type type = symbol.getDeclType();
                if ((type == Symbol.Type.VAR || type == Symbol.Type.FUNCTION_VAR)
                        && forbidden.test(symbol.getName())) {
                    addError("msg.var.redecl", symbol.getName());
                }
            }
        }
        reportErrorsIfExists(baseLineno);

        // add comments to root in lexical order
        if (scannedComments != null) {
            // If we find a comment beyond end of our last statement or
            // function, extend the root bounds to the end of that comment.
            int last = scannedComments.size() - 1;
            end = Math.max(end, getNodeEnd(scannedComments.get(last)));
            for (Comment c : scannedComments) {
                root.addComment(c);
            }
        }

        root.setLength(end - pos);
        root.setSourceName(sourceURI);
        root.setBaseLineno(baseLineno);
        root.setEndLineno(ts.getLineno());
        root.doAnnexBHoisting();
        return root;
    }

    private AstNode parseFunctionBody(int type, FunctionNode fnNode) throws IOException {
        boolean isExpressionClosure = false;
        if (!matchToken(Token.LC, true)) {
            if (compilerEnv.getLanguageVersion() < Context.VERSION_1_8
                    && type != FunctionNode.ARROW_FUNCTION) {
                reportError("msg.no.brace.body");
            } else {
                isExpressionClosure = true;
            }
        }
        boolean isArrow = type == FunctionNode.ARROW_FUNCTION;
        ++nestingOfFunction;
        int pos = ts.tokenBeg;
        Block pn = new Block(pos); // starts at LC position

        // Function code that is supplied as the arguments to the built-in
        // Function, Generator, and AsyncFunction constructors is strict mode code
        // if the last argument is a String that when processed is a FunctionBody
        // that begins with a Directive Prologue that contains a Use Strict Directive.
        boolean inDirectivePrologue = true;
        legacyEscapeInPrologue = false;
        boolean savedStrictMode = inUseStrictDirective;
        boolean savedInSingleStatementContext = inSingleStatementContext;
        boolean savedInSingleStatementDeclContext = inSingleStatementDeclContext;
        boolean savedLetInSingleStatement = letInSingleStatement;
        if (compilerEnv.isLatestEcmaScript()) {
            // if (a) f(function () { let x; }): the body is not in the if's statement position
            inSingleStatementContext = false;
            inSingleStatementDeclContext = false;
            letInSingleStatement = false;
        }

        pn.setLineColumnNumber(lineNumber(), columnNumber());
        try {
            if (isExpressionClosure) {
                AstNode returnValue = assignExpr();
                ReturnStatement n =
                        new ReturnStatement(
                                returnValue.getPosition(), returnValue.getLength(), returnValue);
                // expression closure flag is required on both nodes
                n.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
                n.setLineColumnNumber(returnValue.getLineno(), returnValue.getColumn());
                pn.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
                if (isArrow) {
                    n.putProp(Node.ARROW_FUNCTION_PROP, Boolean.TRUE);
                }
                pn.addStatement(n);
                pn.setLength(n.getLength());
            } else {
                bodyLoop:
                for (; ; ) {
                    AstNode n;
                    int tt = peekToken();
                    switch (tt) {
                        case Token.ERROR:
                        case Token.EOF:
                        case Token.RC:
                            break bodyLoop;
                        case Token.COMMENT:
                            consumeToken();
                            n = scannedComments.get(scannedComments.size() - 1);
                            break;
                        case Token.FUNCTION:
                            consumeToken();
                            n = function(FunctionNode.FUNCTION_STATEMENT);
                            break;
                        case Token.NAME:
                            if (isAsyncFunctionStart()) {
                                n = asyncFunction(FunctionNode.FUNCTION_STATEMENT);
                                break;
                            }
                            n = statement();
                            inDirectivePrologue = false;
                            break;
                        default:
                            n = statement();
                            if (inDirectivePrologue) {
                                String directive = getDirective(n);
                                if (directive == null) {
                                    inDirectivePrologue = false;
                                } else if ("use strict".equals(directive)) {
                                    if (fnNode.getDefaultParams() != null) {
                                        reportError("msg.default.args.use.strict");
                                    }
                                    // "use strict" not allowed with non-simple params
                                    if (fnNode.hasRestParameter()) {
                                        reportError("msg.rest.param.use.strict");
                                    }
                                    if (fnNode.getProp(Node.DESTRUCTURING_PARAMS) != null
                                            && compilerEnv.isLatestEcmaScript()) {
                                        reportError("msg.destruct.param.use.strict");
                                    }
                                    checkLegacyEscapeInPrologue();
                                    inUseStrictDirective = true;
                                    applyStrictToPeekedToken();
                                    fnNode.setInStrictMode(true);
                                }
                            }
                            break;
                    }
                    pn.addStatement(n);
                }
                int end = ts.tokenEnd;
                if (mustMatchToken(Token.RC, "msg.no.brace.after.body", true)) end = ts.tokenEnd;
                pn.setLength(end - pos);
            }
        } catch (ParserException e) {
            // Ignore it
        } finally {
            --nestingOfFunction;
            inUseStrictDirective = savedStrictMode;
            inSingleStatementContext = savedInSingleStatementContext;
            inSingleStatementDeclContext = savedInSingleStatementDeclContext;
            letInSingleStatement = savedLetInSingleStatement;
        }

        getAndResetJsDoc();
        return pn;
    }

    /** "\1"; "use strict": a legacy escape before the directive is an error too. */
    private void checkLegacyEscapeInPrologue() {
        if (legacyEscapeInPrologue && compilerEnv.isLatestEcmaScript()) {
            reportError("msg.no.octal.escape.strict");
        }
    }

    /** An import or export declaration (16.2.2, 16.2.3): the keyword is the peeked token. */
    /** Whether the peeked import starts an expression: import(...) or import.meta. */
    private boolean isImportExpressionStart() {
        if (!"import".equals(ts.getString())) {
            return false;
        }
        TokenStream.LookAhead next = ts.lookAhead();
        return next != null && (next.next == '(' || next.next == '.');
    }

    /**
     * ImportCall (13.3.10) or ImportMeta (13.3.12). import.meta is only in module code, where it is
     * rewritten to the import.meta object the module record hands to the module body.
     */
    private AstNode importExpression() throws IOException {
        consumeToken();
        checkEscapedKeyword();
        int pos = ts.tokenBeg;
        int lineno = lineNumber(), column = columnNumber();
        if (matchToken(Token.DOT, true)) {
            if (!matchToken(Token.NAME, true)
                    || !"meta".equals(ts.getString())
                    || ts.nameHadEscape) {
                reportError("msg.syntax");
                return makeErrorNode();
            }
            if (moduleInfo == null) {
                reportError("msg.import.meta");
            } else {
                // a call, which no assignment or destructuring can target
                moduleInfo.replace(pos, ts.tokenEnd, ModuleInfo.RECORD_PARAM + ".importMeta()");
            }
            // not an assignment target (the rewritten body reads the record's property)
            KeywordLiteral meta = new KeywordLiteral(pos, ts.tokenEnd - pos, Token.THIS);
            meta.setLineColumnNumber(lineno, column);
            return meta;
        }
        mustMatchToken(Token.LP, "msg.no.paren.arg", true);
        boolean wasInForInit = inForInit;
        inForInit = false;
        ImportCall call = new ImportCall(pos, 0);
        try {
            if (peekToken() == Token.DOTDOTDOT || peekToken() == Token.RP) {
                // the specifier is one AssignmentExpression: neither absent nor spread
                reportError("msg.syntax");
            }
            call.setSpecifier(assignExpr());
            if (matchToken(Token.COMMA, true) && peekToken() != Token.RP) {
                call.setOptions(assignExpr());
                matchToken(Token.COMMA, true);
            }
        } finally {
            inForInit = wasInForInit;
        }
        mustMatchToken(Token.RP, "msg.no.paren.arg", true);
        call.setLength(ts.tokenEnd - pos);
        call.setReferrer(sourceURI);
        call.setLineColumnNumber(lineno, column);
        return call;
    }

    private AstNode moduleItem() throws IOException {
        boolean isImport = "import".equals(ts.getString());
        consumeToken();
        int start = ts.tokenBeg;
        int lineno = lineNumber(), column = columnNumber();
        AstNode n = isImport ? importDeclaration(start) : exportDeclaration(start);
        n.setLineColumnNumber(lineno, column);
        return n;
    }

    private AstNode importDeclaration(int start) throws IOException {
        List<String[]> bindings = new ArrayList<>(); // {importName, localName}
        boolean hasClause = peekToken() != Token.STRING;
        if (hasClause) {
            if (peekToken() == Token.NAME) {
                consumeToken();
                bindings.add(new String[] {"default", importedBinding(ts.getString())});
                if (!matchToken(Token.COMMA, true)) {
                    return finishImport(start, bindings, true);
                }
            }
            if (matchToken(Token.MUL, true)) {
                expectContextualKeyword("as");
                mustMatchToken(Token.NAME, "msg.syntax", true);
                bindings.add(new String[] {ModuleInfo.NAMESPACE, importedBinding(ts.getString())});
            } else if (mustMatchToken(Token.LC, "msg.syntax", true)) {
                while (!matchToken(Token.RC, true)) {
                    boolean isString = peekToken() == Token.STRING;
                    String importName = moduleExportName();
                    boolean isIdentifier = !isString && currentToken == Token.NAME;
                    String localName;
                    if (matchContextualKeyword("as")) {
                        mustMatchToken(Token.NAME, "msg.syntax", true);
                        localName = importedBinding(ts.getString());
                    } else {
                        if (!isIdentifier) {
                            reportError("msg.syntax");
                        }
                        localName = importedBinding(importName);
                    }
                    bindings.add(new String[] {importName, localName});
                    if (!matchToken(Token.COMMA, true)) {
                        mustMatchToken(Token.RC, "msg.syntax", true);
                        break;
                    }
                }
            }
        }
        return finishImport(start, bindings, hasClause);
    }

    private AstNode finishImport(int start, List<String[]> bindings, boolean hasClause)
            throws IOException {
        if (hasClause) {
            expectContextualKeyword("from");
        }
        ModuleInfo.ModuleRequest request = moduleRequest();
        for (String[] b : bindings) {
            moduleInfo.importEntries.add(new ModuleInfo.ImportEntry(request, b[0], b[1]));
        }
        return blankModuleItem(start, requestEnd);
    }

    /** The end of the last module request parsed by #moduleRequest. */
    private int requestEnd;

    /** A ModuleSpecifier and its optional WithClause (16.2.2). */
    private ModuleInfo.ModuleRequest moduleRequest() throws IOException {
        mustMatchToken(Token.STRING, "msg.syntax", true);
        String specifier = ts.getString();
        // the end of the request, before a lookahead past it
        requestEnd = ts.tokenEnd;
        Map<String, String> attributes = new LinkedHashMap<>();
        if (matchToken(Token.WITH, true)) {
            mustMatchToken(Token.LC, "msg.syntax", true);
            while (!matchToken(Token.RC, true)) {
                int tt = peekToken();
                consumeToken();
                String key;
                if (tt == Token.STRING || tt == Token.NAME || tt == Token.RESERVED) {
                    key = ts.getString();
                } else {
                    key = Token.keywordToName(tt);
                    if (key == null) {
                        reportError("msg.syntax");
                        return null;
                    }
                }
                mustMatchToken(Token.COLON, "msg.syntax", true);
                mustMatchToken(Token.STRING, "msg.syntax", true);
                if (attributes.put(key, ts.getString()) != null) {
                    reportError("msg.import.dup.attribute", key);
                }
                if (!matchToken(Token.COMMA, true)) {
                    mustMatchToken(Token.RC, "msg.syntax", true);
                    break;
                }
            }
            requestEnd = ts.tokenEnd;
        }
        ModuleInfo.ModuleRequest request = new ModuleInfo.ModuleRequest(specifier, attributes);
        moduleInfo.addRequest(request);
        return request;
    }

    /** The binding of an import (the current token): a const in the module scope. */
    private String importedBinding(String name) {
        if ("eval".equals(name) || "arguments".equals(name)) {
            reportError("msg.bad.id.strict", name);
        }
        defineSymbol(Token.CONST, name);
        return name;
    }

    /** An IdentifierName or a string literal (16.2.2 ModuleExportName). */
    private String moduleExportName() throws IOException {
        int tt = peekToken();
        consumeToken();
        if (tt == Token.STRING) {
            String name = ts.getString();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isHighSurrogate(c)
                                && (i + 1 == name.length()
                                        || !Character.isLowSurrogate(name.charAt(i + 1)))
                        || Character.isLowSurrogate(c)
                                && (i == 0 || !Character.isHighSurrogate(name.charAt(i - 1)))) {
                    // IsStringWellFormedUnicode
                    reportError("msg.syntax");
                }
            }
            return name;
        }
        if (tt == Token.NAME || tt == Token.RESERVED) {
            return ts.getString();
        }
        String keyword = Token.keywordToName(tt);
        if (keyword == null) {
            reportError("msg.syntax");
            return "";
        }
        return keyword;
    }

    private boolean matchContextualKeyword(String word) throws IOException {
        if (peekToken() == Token.NAME && word.equals(ts.getString()) && !ts.nameHadEscape) {
            consumeToken();
            return true;
        }
        return false;
    }

    private void expectContextualKeyword(String word) throws IOException {
        if (!matchContextualKeyword(word)) {
            reportError("msg.syntax");
        }
    }

    /** The end of an import or export: a semicolon, or ASI; the statement is blanked out. */
    private AstNode blankModuleItem(int start, int end) throws IOException {
        int tt = peekFlaggedToken();
        if ((tt & CLEAR_TI_MASK) == Token.SEMI) {
            consumeToken();
            end = ts.tokenEnd;
        } else if ((tt & CLEAR_TI_MASK) != Token.EOF
                && (tt & CLEAR_TI_MASK) != Token.RC
                && (tt & TI_AFTER_EOL) == 0) {
            reportError("msg.no.semi.stmt");
        }
        moduleInfo.blank(start, end);
        EmptyStatement empty = new EmptyStatement(start, end - start);
        return empty;
    }

    private void addExport(
            String exportName,
            ModuleInfo.ModuleRequest moduleRequest,
            String importName,
            String local)
            throws IOException {
        if (exportName != null) {
            for (ModuleInfo.ExportEntry ee : moduleInfo.exportEntries) {
                if (exportName.equals(ee.exportName())) {
                    reportError("msg.module.dup.export", exportName);
                }
            }
        }
        moduleInfo.exportEntries.add(
                new ModuleInfo.ExportEntry(exportName, moduleRequest, importName, local));
    }

    private AstNode exportDeclaration(int start) throws IOException {
        int tt = peekToken();
        if (tt == Token.MUL) {
            consumeToken();
            String exportName = null;
            if (matchContextualKeyword("as")) {
                exportName = moduleExportName();
            }
            expectContextualKeyword("from");
            ModuleInfo.ModuleRequest request = moduleRequest();
            addExport(
                    exportName,
                    request,
                    exportName == null ? ModuleInfo.ALL_BUT_DEFAULT : ModuleInfo.NAMESPACE,
                    null);
            return blankModuleItem(start, requestEnd);
        }
        if (tt == Token.LC) {
            consumeToken();
            List<String[]> specs = new ArrayList<>(); // {local or import name, export name}
            List<Boolean> localIsReference = new ArrayList<>();
            while (!matchToken(Token.RC, true)) {
                boolean isString = peekToken() == Token.STRING;
                String name = moduleExportName();
                localIsReference.add(!isString && currentToken == Token.NAME);
                String exportName = matchContextualKeyword("as") ? moduleExportName() : name;
                specs.add(new String[] {name, exportName});
                if (!matchToken(Token.COMMA, true)) {
                    mustMatchToken(Token.RC, "msg.syntax", true);
                    break;
                }
            }
            int rcEnd = ts.tokenEnd;
            if (matchContextualKeyword("from")) {
                ModuleInfo.ModuleRequest request = moduleRequest();
                for (String[] spec : specs) {
                    addExport(spec[1], request, spec[0], null);
                }
                return blankModuleItem(start, requestEnd);
            } else {
                for (int i = 0; i < specs.size(); i++) {
                    if (!localIsReference.get(i)) {
                        // export { "a" } or export { if }: a local binding must be an identifier
                        reportError("msg.syntax");
                    }
                    addExport(specs.get(i)[1], null, null, specs.get(i)[0]);
                }
                return blankModuleItem(start, rcEnd);
            }
        }
        if (tt == Token.DEFAULT) {
            consumeToken();
            if (ts.nameHadEscape) {
                reportError("msg.invalid.escape");
            }
            return exportDefault(start);
        }
        // export VariableStatement / Declaration: the declared names are exported
        int keywordEnd = start + "export".length();
        AstNode decl;
        List<String> names = new ArrayList<>();
        if (tt == Token.VAR || tt == Token.CONST || tt == Token.LET) {
            consumeToken();
            VariableDeclaration vars = variables(tt, ts.tokenBeg, true);
            autoInsertSemicolon(vars);
            for (VariableInitializer vi : vars.getVariables()) {
                collectBoundNames(vi.getTarget(), names);
            }
            decl = vars;
        } else if (tt == Token.FUNCTION) {
            consumeToken();
            FunctionNode fn = function(FunctionNode.FUNCTION_STATEMENT);
            if (fn.getFunctionName() == null) {
                reportError("msg.syntax");
            } else {
                names.add(fn.getFunctionName().getIdentifier());
            }
            decl = fn;
        } else if (tt == Token.RESERVED && isClassKeyword()) {
            decl = classDeclaration();
            collectBoundNames(
                    ((VariableDeclaration) decl).getVariables().get(0).getTarget(), names);
        } else {
            reportError("msg.syntax");
            return makeErrorNode();
        }
        for (String name : names) {
            addExport(name, null, null, name);
        }
        moduleInfo.blank(start, keywordEnd);
        return decl;
    }

    /** export default ... (16.2.3): a declaration, or an expression bound to *default*. */
    private AstNode exportDefault(int start) throws IOException {
        int afterDefault = ts.tokenEnd;
        int tt = peekToken();
        boolean isAsync = tt == Token.NAME && isAsyncFunctionStart();
        if (tt == Token.FUNCTION || isAsync) {
            FunctionNode fn;
            if (isAsync) {
                fn = asyncFunction(FunctionNode.FUNCTION_STATEMENT);
            } else {
                consumeToken();
                fn = function(FunctionNode.FUNCTION_STATEMENT);
            }
            if (fn.getFunctionName() != null) {
                addExport("default", null, null, fn.getFunctionName().getIdentifier());
                moduleInfo.blank(start, afterDefault);
            } else {
                // function () {}: a declaration of *default*, named "default"
                addExport("default", null, null, ModuleInfo.DEFAULT_BINDING);
                moduleInfo.anonymousDefaultFunction = true;
                int lp = fn.getPosition() + fn.getLp();
                moduleInfo.replace(
                        start,
                        lp,
                        (fn.isAsync()
                                        ? "async function "
                                        : fn.isES6Generator() ? "function* " : "function ")
                                + ModuleInfo.DEFAULT_BINDING);
            }
            return fn;
        }
        if (tt == Token.RESERVED && isClassKeyword()) {
            TokenStream.LookAhead next = ts.lookAhead();
            if (next != null && next.startsIdentifier() && !"extends".equals(next.word)) {
                AstNode decl = classDeclaration();
                List<String> names = new ArrayList<>();
                collectBoundNames(
                        ((VariableDeclaration) decl).getVariables().get(0).getTarget(), names);
                addExport("default", null, null, names.get(0));
                moduleInfo.blank(start, afterDefault);
                return decl;
            }
            // class {}: a declaration of *default*, named "default"
            ClassNode cls = classDefinition(false);
            addExport("default", null, null, ModuleInfo.DEFAULT_BINDING);
            int end = getNodeEnd(cls);
            moduleInfo.replace(
                    start, afterDefault, "const " + ModuleInfo.DEFAULT_BINDING + " = {default: ");
            moduleInfo.insert(end, "}.default;");
            return cls;
        }
        // export default AssignmentExpression;
        AstNode expr = assignExpr();
        addExport("default", null, null, ModuleInfo.DEFAULT_BINDING);
        int end = getNodeEnd(expr);
        moduleInfo.replace(
                start, afterDefault, "const " + ModuleInfo.DEFAULT_BINDING + " = {default: (");
        moduleInfo.insert(end, ")}.default");
        ExpressionStatement stmt = new ExpressionStatement(expr, false);
        autoInsertSemicolon(stmt);
        return stmt;
    }

    /**
     * The early errors of a module (16.2.1.1): an exported local name must be declared, and the
     * export entries are sorted.
     */
    private void checkModuleExports(AstRoot root) {
        Set<String> imported = new HashSet<>();
        for (ModuleInfo.ImportEntry ie : moduleInfo.importEntries) {
            imported.add(ie.localName());
        }
        // the names of destructuring declarations are not symbols until the IR is built
        List<String> destructured = new ArrayList<>();
        root.visit(
                node -> {
                    if (node instanceof FunctionNode) {
                        return false;
                    }
                    if (node instanceof VariableDeclaration
                            && (node.getType() == Token.VAR || node.getParent() == root)) {
                        for (VariableInitializer vi : ((VariableDeclaration) node).getVariables()) {
                            if (vi.isDestructuring()) {
                                collectBoundNames(vi.getTarget(), destructured);
                            }
                        }
                    }
                    return true;
                });
        for (ModuleInfo.ExportEntry ee : moduleInfo.exportEntries) {
            String local = ee.localName();
            if (ee.moduleRequest() == null
                    && !ModuleInfo.DEFAULT_BINDING.equals(local)
                    && !imported.contains(local)
                    && root.getSymbol(local) == null
                    && !destructured.contains(local)) {
                reportError("msg.module.undeclared.export", local);
            }
        }
        moduleInfo.sortExportEntries();
    }

    private String getDirective(AstNode n) {
        if (n instanceof ExpressionStatement) {
            AstNode e = ((ExpressionStatement) n).getExpression();
            if (e instanceof StringLiteral) {
                String value = ((StringLiteral) e).getValue();
                // 'use str\<LF>ict' is a directive, but not a Use Strict Directive: that is the
                // exact source text "use strict"
                return e.getLength() == value.length() + 2 || !compilerEnv.isLatestEcmaScript()
                        ? value
                        : "";
            }
        }
        return null;
    }

    private void parseFunctionParams(FunctionNode fnNode) throws IOException {
        ++nestingOfFunctionParams;
        FunctionNode savedFunctionOfParams = functionOfParams;
        functionOfParams = fnNode;
        try {
            if (matchToken(Token.RP, true)) {
                fnNode.setRp(ts.tokenBeg - fnNode.getPosition());
                return;
            }
            // Would prefer not to call createDestructuringAssignment until codegen,
            // but the symbol definitions have to happen now, before body is parsed.
            Map<String, Node> destructuring = null;
            Map<String, AstNode> destructuringDefault = null;

            do {
                int tt = peekToken();
                if (tt == Token.RP) {
                    if (fnNode.hasRestParameter()) {
                        // Error: parameter after rest parameter
                        reportError("msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                    }

                    fnNode.putIntProp(Node.TRAILING_COMMA, 1);
                    break;
                }
                if (tt == Token.LB || tt == Token.LC) {
                    if (fnNode.hasRestParameter()) {
                        // Error: parameter after rest parameter
                        reportError("msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                    }

                    AstNode expr = destructuringAssignExpr();
                    if (destructuring == null) {
                        destructuring = newDestructuringMap();
                    }

                    if (expr instanceof Assignment) {
                        // We have default arguments inside destructured function parameters
                        // eg: f([x = 1] = [2]) { ... }, transform this into:
                        // f(x) {
                        //      if ($1 == undefined)
                        //          var $1 = [2];
                        //      if (x == undefined)
                        //          if (($1[0]) == undefined)
                        //              var x = 1;
                        //          else
                        //              var x = $1[0];
                        // }
                        // fnNode.addParam(name)
                        AstNode lhs = ((Assignment) expr).getLeft(); // [x = 1]
                        AstNode rhs = ((Assignment) expr).getRight(); // [2]
                        markDestructuring(lhs);
                        fnNode.addParam(lhs);
                        String pname = currentScriptOrFn.getNextTempName();
                        defineSymbol(Token.LP, pname, false);
                        if (destructuringDefault == null) {
                            destructuringDefault = new HashMap<>();
                        }
                        destructuring.put(pname, lhs);
                        destructuringDefault.put(pname, rhs);
                    } else {
                        markDestructuring(expr);
                        fnNode.addParam(expr);
                        // Destructuring assignment for parameters: add a dummy
                        // parameter name, and add a statement to the body to initialize
                        // variables from the destructuring assignment
                        String pname = currentScriptOrFn.getNextTempName();
                        defineSymbol(Token.LP, pname, false);
                        destructuring.put(pname, expr);
                    }
                } else {
                    boolean wasRest = false;
                    int restStartLineno = -1, restStartColumn = -1;
                    if (tt == Token.DOTDOTDOT) {
                        if (fnNode.hasRestParameter()) {
                            // Error: parameter after rest parameter
                            reportError(
                                    "msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                        }

                        fnNode.setHasRestParameter(true);
                        wasRest = true;
                        consumeToken();
                        restStartLineno = lineNumber();
                        restStartColumn = columnNumber();
                        int next = peekToken();
                        if ((next == Token.LB || next == Token.LC)
                                && compilerEnv.isLatestEcmaScript()) {
                            // a binding pattern as rest element: function f(...[a, b])
                            AstNode pattern = destructuringPrimaryExpr();
                            markDestructuring(pattern);
                            fnNode.addParam(pattern);
                            String pname = currentScriptOrFn.getNextTempName();
                            defineSymbol(Token.LP, pname, false);
                            if (destructuring == null) {
                                destructuring = new LinkedHashMap<>();
                            }
                            destructuring.put(pname, pattern);
                            continue;
                        }
                    }

                    if (matchToken(Token.UNDEFINED, true)
                            || mustMatchToken(Token.NAME, "msg.no.parm", true)) {

                        if (!wasRest && fnNode.hasRestParameter()) {
                            // Error: parameter after rest parameter
                            reportError(
                                    "msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                        }

                        Name paramNameNode = createNameNode();
                        if (wasRest) {
                            paramNameNode.setLineColumnNumber(restStartLineno, restStartColumn);
                        }
                        Comment jsdocNodeForName = getAndResetJsDoc();
                        if (jsdocNodeForName != null) {
                            paramNameNode.setJsDocNode(jsdocNodeForName);
                        }
                        fnNode.addParam(paramNameNode);
                        String paramName = ts.getString();
                        defineSymbol(Token.LP, paramName);
                        if (this.inUseStrictDirective) {
                            if ("eval".equals(paramName) || "arguments".equals(paramName)) {
                                reportError("msg.bad.id.strict", paramName);
                            }
                        }

                        if (matchToken(Token.ASSIGN, true)) {
                            if (wasRest && compilerEnv.isLatestEcmaScript()) {
                                // a rest parameter has no initializer
                                reportError("msg.parm.after.rest");
                            }
                            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                                fnNode.putDefaultParams(paramName, assignExpr());
                            } else {
                                reportError("msg.default.args");
                            }
                        }
                    } else {
                        fnNode.addParam(makeErrorNode());
                    }
                }
            } while (matchToken(Token.COMMA, true));

            if (destructuring != null) {
                Node destructuringNode = new Node(Token.COMMA);
                // Add assignment helper for each destructuring parameter
                for (Map.Entry<String, Node> param : destructuring.entrySet()) {
                    AstNode defaultValue = null;
                    if (destructuringDefault != null) {
                        defaultValue = destructuringDefault.get(param.getKey());
                    }
                    Node assign =
                            createDestructuringAssignment(
                                    Token.VAR,
                                    param.getValue(),
                                    createName(param.getKey()),
                                    defaultValue);
                    destructuringNode.addChildToBack(assign);
                }
                fnNode.putProp(Node.DESTRUCTURING_PARAMS, destructuringNode);
            }

            if (mustMatchToken(Token.RP, "msg.no.paren.after.parms", true)) {
                fnNode.setRp(ts.tokenBeg - fnNode.getPosition());
            }
        } finally {
            --nestingOfFunctionParams;
            functionOfParams = savedFunctionOfParams;
        }
    }

    private FunctionNode function(int type) throws IOException {
        return function(type, false);
    }

    private FunctionNode function(int type, boolean isMethodDefiniton) throws IOException {
        // a generator method (*m() {}) is known before its body is parsed: yield is a keyword there
        boolean isGenerator = isMethodDefiniton && methodIsGenerator;
        methodIsGenerator = false;
        boolean isAsync = functionIsAsync;
        functionIsAsync = false;
        int syntheticType = type;
        int baseLineno = lineNumber(); // line number where source starts
        int functionSourceStart = ts.tokenBeg; // start of "function" kwd
        int functionStartColumn = columnNumber();
        Name name = null;
        AstNode memberExprNode = null;

        do {
            if (matchToken(Token.NAME, true) || matchToken(Token.UNDEFINED, true)) {
                name = createNameNode(true, Token.NAME);
                if (inUseStrictDirective) {
                    String id = name.getIdentifier();
                    if ("eval".equals(id) || "arguments".equals(id)) {
                        reportError("msg.bad.id.strict", id);
                    }
                }
                if (!matchToken(Token.LP, true)) {
                    if (compilerEnv.isAllowMemberExprAsFunctionName()) {
                        AstNode memberExprHead = name;
                        name = null;
                        memberExprNode = memberExprTail(false, memberExprHead);
                    }
                    mustMatchToken(Token.LP, "msg.no.paren.parms", true);
                }
            } else if (type == FunctionNode.FUNCTION_EXPRESSION
                    && !isGenerator
                    && !inUseStrictDirective
                    && compilerEnv.isLatestEcmaScript()
                    && matchToken(Token.YIELD, true)) {
                // the name of a function expression is bound in that function: not a generator
                name = createNameNode(true, Token.NAME);
                mustMatchToken(Token.LP, "msg.no.paren.parms", true);
            } else if (type == FunctionNode.FUNCTION_EXPRESSION
                    && !isAsync
                    && inStaticBlock
                    && moduleInfo == null
                    && matchToken(Token.AWAIT, true)) {
                // likewise, await names a function expression in a class static block
                name = createNameNode(true, Token.NAME);
                mustMatchToken(Token.LP, "msg.no.paren.parms", true);
            } else if (matchToken(Token.LP, true)) {
                // Anonymous function:  leave name as null
            } else if (matchToken(Token.MUL, true)
                    && (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6)) {
                // ES6 generator function
                isGenerator = true;
                continue;
            } else {
                if (compilerEnv.isAllowMemberExprAsFunctionName()) {
                    // Note that memberExpr can not start with '(' like
                    // in function (1+2).toString(), because 'function (' already
                    // processed as anonymous function
                    memberExprNode = memberExpr(false);
                }
                mustMatchToken(Token.LP, "msg.no.paren.parms", true);
            }
            break;
        } while (isGenerator);
        int lpPos = currentToken == Token.LP ? ts.tokenBeg : -1;

        if (memberExprNode != null) {
            syntheticType = FunctionNode.FUNCTION_EXPRESSION;
        }
        if (isAsync
                && type == FunctionNode.FUNCTION_EXPRESSION
                && name != null
                && "await".equals(name.getIdentifier())) {
            // the name of an async function expression is bound in it, where await is a keyword
            reportError("msg.syntax");
        }
        if (isGenerator
                && type == FunctionNode.FUNCTION_EXPRESSION
                && name != null
                && compilerEnv.isLatestEcmaScript()
                && "yield".equals(name.getIdentifier())) {
            // the name of a generator expression is bound in the generator, where yield is a
            // keyword
            reportError("msg.syntax");
        }

        defineFunctionSymbol(type, syntheticType, name, isGenerator || isAsync);

        FunctionNode fnNode = new FunctionNode(functionSourceStart, name);
        fnNode.setMethodDefinition(isMethodDefiniton);
        fnNode.setFunctionType(type);
        if (inUseStrictDirective && compilerEnv.isLatestEcmaScript()) {
            // strict code around a function that its parent script is not (a class heritage)
            fnNode.setInStrictMode(true);
        }

        // ES2024, B.3.2.1: register annexB candidate after FunctionNode is created.
        if (type == FunctionNode.FUNCTION_BLOCK_SCOPED
                && !inUseStrictDirective
                && !isAsync
                && !(isGenerator && compilerEnv.isLatestEcmaScript())
                && name != null
                && name.length() > 0) {
            Symbol letSym = currentScope.getSymbol(name.getIdentifier());
            currentScriptOrFn.addAnnexBFunction(letSym, fnNode);
        }
        if (isAsync && isGenerator) {
            fnNode.setIsAsyncGenerator();
        } else if (isAsync) {
            fnNode.setIsAsync();
        } else if (isGenerator) {
            fnNode.setIsES6Generator();
        }
        if (lpPos != -1) fnNode.setLp(lpPos - functionSourceStart);

        fnNode.setJsDocNode(getAndResetJsDoc());

        PerFunctionVariables savedVars = new PerFunctionVariables(fnNode);
        boolean wasInsideMethod = insideMethod;
        insideMethod = isMethodDefiniton;
        try {
            parseFunctionParams(fnNode);
            AstNode body = parseFunctionBody(type, fnNode);
            fnNode.setBody(body);
            checkParamsAfterBody(fnNode, isMethodDefiniton);
            separateBodyVars(fnNode);
            int end = functionSourceStart + body.getPosition() + body.getLength();
            fnNode.setRawSourceBounds(functionSourceStart, end);
            fnNode.setLength(end - functionSourceStart);

            if (compilerEnv.isStrictMode() && !fnNode.getBody().hasConsistentReturnUsage()) {
                String msg =
                        (name != null && name.length() > 0)
                                ? "msg.no.return.value"
                                : "msg.anon.no.return.value";
                addStrictWarning(msg, name == null ? "" : name.getIdentifier());
            }
        } finally {
            savedVars.restore();
            insideMethod = wasInsideMethod;
        }

        if (memberExprNode != null) {
            // TODO(stevey): fix missing functionality
            Kit.codeBug();
            fnNode.setMemberExprNode(memberExprNode); // rewrite later
            /* old code:
            if (memberExprNode != null) {
                pn = nf.createAssignment(Token.ASSIGN, memberExprNode, pn);
                if (functionType != FunctionNode.FUNCTION_EXPRESSION) {
                    // XXX check JScript behavior: should it be createExprStatement?
                    pn = nf.createExprStatementNoReturn(pn, baseLineno);
                }
            }
            */
        }

        fnNode.setSourceName(sourceURI);
        fnNode.setLineColumnNumber(baseLineno, functionStartColumn);
        fnNode.setEndLineno(lineNumber());

        // Set the parent scope.  Needed for finding undeclared vars.
        // Have to wait until after parsing the function to set its parent
        // scope, since defineSymbol needs the defining-scope check to stop
        // at the function boundary when checking for redeclarations.
        if (compilerEnv.isIdeMode()) {
            fnNode.setParentScope(currentScope);
        }
        fnNode.doAnnexBHoisting();
        return fnNode;
    }

    private void defineFunctionSymbol(int type, int syntheticType, Name name, boolean isGenerator) {
        if (syntheticType != FunctionNode.FUNCTION_EXPRESSION
                && name != null
                && name.length() > 0) {
            if (type == FunctionNode.FUNCTION_BLOCK_SCOPED) {
                // Block-scoped function in strict mode: define as let-like binding
                if (inUseStrictDirective || currentScope != currentScriptOrFn) {
                    defineSymbol(
                            isGenerator ? Symbol.Type.LET : Symbol.Type.FUNCTION_LET,
                            name.getIdentifier());
                    return;
                }
            }
            if (moduleInfo != null && currentScope == currentScriptOrFn) {
                // the top-level functions of a module are lexically declared (16.2.1.1)
                defineSymbol(Symbol.Type.FUNCTION_LET, name.getIdentifier());
                return;
            }
            // Function statements define a symbol in the enclosing scope
            defineSymbol(Symbol.Type.FUNCTION_VAR, name.getIdentifier());
        }
    }

    private AstNode arrowFunction(AstNode params, int startLine, int startColumn, int asyncStart)
            throws IOException {
        boolean isAsync = functionIsAsync;
        functionIsAsync = false;
        int baseLineno = lineNumber(); // line number where source starts
        int functionSourceStart =
                asyncStart >= 0
                        ? asyncStart
                        : params != null ? params.getPosition() : -1; // start of "function" kwd

        FunctionNode fnNode = new FunctionNode(functionSourceStart);
        fnNode.setFunctionType(FunctionNode.ARROW_FUNCTION);
        if (inUseStrictDirective && compilerEnv.isLatestEcmaScript()) {
            fnNode.setInStrictMode(true);
        }
        if (isAsync) {
            fnNode.setIsAsync();
        } else if (params != null && inAsyncFunction()) {
            checkAwaitInArrowParams(params, false);
        }
        if (params != null && compilerEnv.isLatestEcmaScript()) {
            checkYieldInArrowParams(params);
        }
        fnNode.setJsDocNode(getAndResetJsDoc());

        // Would prefer not to call createDestructuringAssignment until codegen,
        // but the symbol definitions have to happen now, before body is parsed.
        Map<String, Node> destructuring = newDestructuringMap();
        Map<String, AstNode> destructuringDefault = new HashMap<>();
        Set<String> paramNames = new HashSet<>();

        PerFunctionVariables savedVars = new PerFunctionVariables(fnNode);
        // Intentionally not overwriting "insideMethod" - we want to propagate this from the parent
        // function or scope
        try {
            if (params instanceof ParenthesizedExpression) {
                fnNode.setParens(0, params.getLength());
                if (params.getIntProp(Node.TRAILING_COMMA, 0) == 1) {
                    fnNode.putIntProp(Node.TRAILING_COMMA, 1);
                }
                AstNode p = ((ParenthesizedExpression) params).getExpression();
                if (!(p instanceof EmptyExpression)) {
                    arrowFunctionParams(fnNode, p, destructuring, destructuringDefault, paramNames);
                }
                // shouldn't have trailing comma after rest params
                if (fnNode.hasRestParameter() && fnNode.getIntProp(Node.TRAILING_COMMA, 0) == 1) {
                    reportError("msg.parm.after.rest");
                }
            } else {
                arrowFunctionParams(
                        fnNode, params, destructuring, destructuringDefault, paramNames);
            }
            markEvalInArrowParams(params);

            if (!destructuring.isEmpty()) {
                Node destructuringNode = new Node(Token.COMMA);
                // Add assignment helper for each destructuring parameter
                for (Map.Entry<String, Node> param : destructuring.entrySet()) {
                    AstNode defaultValue = null;
                    if (destructuringDefault != null) {
                        defaultValue = destructuringDefault.get(param.getKey());
                    }
                    Node assign =
                            createDestructuringAssignment(
                                    Token.VAR,
                                    param.getValue(),
                                    createName(param.getKey()),
                                    defaultValue);
                    destructuringNode.addChildToBack(assign);
                }
                fnNode.putProp(Node.DESTRUCTURING_PARAMS, destructuringNode);
            }

            AstNode body = parseFunctionBody(FunctionNode.ARROW_FUNCTION, fnNode);
            fnNode.setBody(body);
            checkParamsAfterBody(fnNode, false);
            separateBodyVars(fnNode);
            int end = functionSourceStart + body.getPosition() + body.getLength();
            fnNode.setRawSourceBounds(functionSourceStart, end);
            fnNode.setLength(end - functionSourceStart);
        } finally {
            savedVars.restore();
        }

        if (fnNode.isGenerator() && !fnNode.isAsync()) {
            reportError("msg.arrowfunction.generator");
            return makeErrorNode();
        }

        fnNode.setSourceName(sourceURI);
        fnNode.setBaseLineno(baseLineno);
        fnNode.setEndLineno(lineNumber());
        fnNode.setLineColumnNumber(startLine, startColumn);

        return fnNode;
    }

    private void arrowFunctionParams(
            FunctionNode fnNode,
            AstNode params,
            Map<String, Node> destructuring,
            Map<String, AstNode> destructuringDefault,
            Set<String> paramNames)
            throws IOException {
        if (params instanceof ArrayLiteral || params instanceof ObjectLiteral) {
            markDestructuring(params);
            fnNode.addParam(params);
            String pname = currentScriptOrFn.getNextTempName();
            defineSymbol(Token.LP, pname, false);
            destructuring.put(pname, params);
            // check for duplicate names in destructuring pattern
            collectDestructuringNames(params, paramNames);
        } else if (params instanceof InfixExpression && params.getType() == Token.COMMA) {
            arrowFunctionParams(
                    fnNode,
                    ((InfixExpression) params).getLeft(),
                    destructuring,
                    destructuringDefault,
                    paramNames);
            arrowFunctionParams(
                    fnNode,
                    ((InfixExpression) params).getRight(),
                    destructuring,
                    destructuringDefault,
                    paramNames);
        } else if (params instanceof Name) {
            fnNode.addParam(params);
            String paramName = ((Name) params).getIdentifier();
            defineSymbol(Token.LP, paramName);

            // disallow duplicates in arrow fns
            if (paramNames.contains(paramName)) {
                addError("msg.dup.param.strict", paramName);
            }
            paramNames.add(paramName);

            if (this.inUseStrictDirective) {
                if ("eval".equals(paramName) || "arguments".equals(paramName)) {
                    reportError("msg.bad.id.strict", paramName);
                }
            }
        } else if (params instanceof Assignment) {
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                AstNode rhs = ((Assignment) params).getRight();
                AstNode lhs = ((Assignment) params).getLeft();
                String paramName;

                /* copy default values for use in IR */
                if (lhs instanceof Name) {
                    paramName = ((Name) lhs).getIdentifier();
                    fnNode.putDefaultParams(paramName, rhs);
                    arrowFunctionParams(
                            fnNode, lhs, destructuring, destructuringDefault, paramNames);
                } else if (lhs instanceof ArrayLiteral || lhs instanceof ObjectLiteral) {
                    markDestructuring(lhs);
                    fnNode.addParam(lhs);
                    String pname = currentScriptOrFn.getNextTempName();
                    defineSymbol(Token.LP, pname, false);
                    destructuring.put(pname, lhs);
                    destructuringDefault.put(pname, rhs);
                    // check for duplicate names in destructuring pattern
                    collectDestructuringNames(lhs, paramNames);
                } else {
                    reportError("msg.no.parm", params.getPosition(), params.getLength());
                    fnNode.addParam(makeErrorNode());
                }
            } else {
                reportError("msg.default.args");
            }
        } else if (params instanceof Spread) {
            // rest parameters in arrow functions
            if (fnNode.hasRestParameter()) {
                reportError("msg.parm.after.rest", params.getPosition(), params.getLength());
            }
            fnNode.setHasRestParameter(true);
            AstNode restParam = ((Spread) params).getExpression();
            if (restParam instanceof Name) {
                fnNode.addParam(restParam);
                String paramName = ((Name) restParam).getIdentifier();
                defineSymbol(Token.LP, paramName);

                // disallow duplicates in arrow functions
                if (paramNames.contains(paramName)) {
                    addError("msg.dup.param.strict", paramName);
                }
                paramNames.add(paramName);

                if (this.inUseStrictDirective) {
                    if ("eval".equals(paramName) || "arguments".equals(paramName)) {
                        reportError("msg.bad.id.strict", paramName);
                    }
                }
            } else if ((restParam instanceof ArrayLiteral || restParam instanceof ObjectLiteral)
                    && compilerEnv.isLatestEcmaScript()) {
                markDestructuring(restParam);
                fnNode.addParam(restParam);
                String pname = currentScriptOrFn.getNextTempName();
                defineSymbol(Token.LP, pname, false);
                destructuring.put(pname, restParam);
                collectDestructuringNames(restParam, paramNames);
            } else {
                reportError("msg.no.parm", restParam.getPosition(), restParam.getLength());
                fnNode.addParam(makeErrorNode());
            }
        } else {
            reportError("msg.no.parm", params.getPosition(), params.getLength());
            fnNode.addParam(makeErrorNode());
        }
    }

    /**
     * The early errors of a parameter list that a "use strict" body can trigger after the fact:
     * eval or arguments as a binding name, and duplicate parameters. Duplicates are also an error
     * in methods and with a non-simple list (ECMA-262 15.2.1); arrow functions check them while
     * parsing their parameters.
     */
    private void checkParamsAfterBody(FunctionNode fnNode, boolean isMethod) {
        if (!compilerEnv.isLatestEcmaScript()) {
            // before VERSION_ECMASCRIPT, only code that is already strict rejects duplicate
            // simple parameters
            if (inUseStrictDirective && fnNode.getFunctionType() != FunctionNode.ARROW_FUNCTION) {
                Set<String> seen = new HashSet<>();
                for (AstNode param : fnNode.getParams()) {
                    if (param instanceof Name && !seen.add(((Name) param).getIdentifier())) {
                        addError("msg.dup.param.strict", ((Name) param).getIdentifier());
                    }
                }
            }
            return;
        }
        boolean strict = inUseStrictDirective || fnNode.isInStrictMode();
        boolean simple =
                fnNode.getDefaultParams() == null
                        && !fnNode.hasRestParameter()
                        && fnNode.getProp(Node.DESTRUCTURING_PARAMS) == null;
        boolean checkDuplicates =
                fnNode.getFunctionType() != FunctionNode.ARROW_FUNCTION
                        && (strict || isMethod || !simple);
        if (!strict && !checkDuplicates) {
            return;
        }
        List<String> names = new ArrayList<>();
        if (strict && fnNode.getFunctionName() != null) {
            names.add(fnNode.getFunctionName().getIdentifier());
        }
        int firstParam = names.size();
        for (AstNode param : fnNode.getParams()) {
            collectBoundNames(param, names);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (strict && ("eval".equals(name) || "arguments".equals(name))) {
                reportError("msg.bad.id.strict", name);
            }
            if (i >= firstParam && checkDuplicates && !seen.add(name)) {
                reportError("msg.dup.param.strict", name);
            }
        }
    }

    /** catch (e) { let e; }: a lexical declaration of the block cannot redeclare a parameter. */
    private void checkCatchParameterRedeclaration(AstNode param, Scope block) {
        Map<String, Symbol> symbols = block.getSymbolTable();
        if (symbols == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        collectBoundNames(param, names);
        for (String name : names) {
            Symbol sym = symbols.get(name);
            if (sym != null && sym.getDeclType() != Symbol.Type.VAR) {
                reportError("msg.let.redecl", name);
            }
        }
    }

    static void collectBoundNames(AstNode pattern, List<String> names) {
        if (pattern instanceof Name) {
            names.add(((Name) pattern).getIdentifier());
        } else if (pattern instanceof ArrayLiteral) {
            for (AstNode elem : ((ArrayLiteral) pattern).getElements()) {
                collectBoundNames(
                        elem instanceof Spread ? ((Spread) elem).getExpression() : elem, names);
            }
        } else if (pattern instanceof ObjectLiteral) {
            for (AbstractObjectProperty prop : ((ObjectLiteral) pattern).getElements()) {
                if (prop instanceof ObjectProperty) {
                    collectBoundNames(((ObjectProperty) prop).getValue(), names);
                } else if (prop instanceof SpreadObjectProperty) {
                    collectBoundNames(((SpreadObjectProperty) prop).getSpreadNode(), names);
                }
            }
        } else if (pattern instanceof Assignment) {
            collectBoundNames(((Assignment) pattern).getLeft(), names);
        } else if (pattern instanceof Spread) {
            collectBoundNames(((Spread) pattern).getExpression(), names);
        }
    }

    /** Collect all parameter names from a destructuring pattern */
    private void collectDestructuringNames(AstNode pattern, Set<String> names) {
        if (pattern instanceof Name) {
            String name = ((Name) pattern).getIdentifier();
            if (names.contains(name)) {
                addError("msg.dup.param.strict", name);
            }
            names.add(name);
        } else if (pattern instanceof ArrayLiteral) {
            for (AstNode elem : ((ArrayLiteral) pattern).getElements()) {
                if (elem instanceof EmptyExpression) {
                    continue;
                }
                if (elem instanceof Spread) {
                    // [...rest] in array destructuring
                    collectDestructuringNames(((Spread) elem).getExpression(), names);
                } else {
                    collectDestructuringNames(elem, names);
                }
            }
        } else if (pattern instanceof ObjectLiteral) {
            for (AbstractObjectProperty prop : ((ObjectLiteral) pattern).getElements()) {
                if (prop instanceof ObjectProperty) {
                    AstNode value = ((ObjectProperty) prop).getValue();
                    collectDestructuringNames(value, names);
                }
            }
        } else if (pattern instanceof Assignment) {
            collectDestructuringNames(((Assignment) pattern).getLeft(), names);
        }
    }

    // This function does not match the closing RC: the caller matches
    // the RC so it can provide a suitable error message if not matched.
    // This means it's up to the caller to set the length of the node to
    // include the closing RC.  The node start pos is set to the
    // absolute buffer start position, and the caller should fix it up
    // to be relative to the parent node.  All children of this block
    // node are given relative start positions and correct lengths.

    private AstNode statements(AstNode parent) throws IOException {
        if (currentToken != Token.LC // assertion can be invalid in bad code
                && !compilerEnv.isIdeMode()) codeBug();
        int pos = ts.tokenBeg;
        AstNode block = parent != null ? parent : new Block(pos);
        block.setLineColumnNumber(lineNumber(), columnNumber());

        int tt;
        while ((tt = peekToken()) > Token.EOF && tt != Token.RC) {
            block.addChild(statement());
        }
        block.setLength(ts.tokenBeg - pos);
        return block;
    }

    private AstNode statements() throws IOException {
        return statements(null);
    }

    private static class ConditionData {
        AstNode condition;
        int lp = -1;
        int rp = -1;
    }

    // parse and return a parenthesized expression
    private ConditionData condition() throws IOException {
        ConditionData data = new ConditionData();

        if (mustMatchToken(Token.LP, "msg.no.paren.cond", true)) data.lp = ts.tokenBeg;

        data.condition = expr(false);

        if (mustMatchToken(Token.RP, "msg.no.paren.after.cond", true)) data.rp = ts.tokenBeg;

        // Report strict warning on code like "if (a = 7) ...". Suppress the
        // warning if the condition is parenthesized, like "if ((a = 7)) ...".
        if (data.condition instanceof Assignment) {
            addStrictWarning(
                    "msg.equal.as.assign",
                    "",
                    data.condition.getPosition(),
                    data.condition.getLength());
        }
        return data;
    }

    private AstNode statement() throws IOException {
        int pos = ts.tokenBeg;
        try {
            AstNode pn = statementHelper();
            if (pn != null) {
                if (compilerEnv.isStrictMode() && !pn.hasSideEffects()) {
                    int beg = pn.getPosition();
                    beg = Math.max(beg, lineBeginningFor(beg));
                    addStrictWarning(
                            pn instanceof EmptyStatement
                                    ? "msg.extra.trailing.semi"
                                    : "msg.no.side.effects",
                            "",
                            beg,
                            nodeEnd(pn) - beg);
                }
                int ntt = peekToken();
                if (ntt == Token.COMMENT
                        && pn.getLineno()
                                == scannedComments.get(scannedComments.size() - 1).getLineno()) {
                    pn.setInlineComment(scannedComments.get(scannedComments.size() - 1));
                    consumeToken();
                }
                return pn;
            }
        } catch (ParserException e) {
            // an ErrorNode was added to the ErrorReporter
        }

        // error:  skip ahead to a probable statement boundary
        guessingStatementEnd:
        for (; ; ) {
            int tt = peekTokenOrEOL();
            consumeToken();
            switch (tt) {
                case Token.ERROR:
                case Token.EOF:
                case Token.EOL:
                case Token.SEMI:
                    break guessingStatementEnd;
            }
        }
        // We don't make error nodes explicitly part of the tree;
        // they get added to the ErrorReporter.  May need to do
        // something different here.
        return new EmptyStatement(pos, ts.tokenBeg - pos);
    }

    private AstNode statementHelper() throws IOException {
        // If the statement is set, then it's been told its label by now.
        if (currentLabel != null && currentLabel.getStatement() != null) currentLabel = null;
        boolean labelledItem = letInSingleStatement;
        letInSingleStatement = false;

        AstNode pn = null;
        int tt = peekToken(), pos = ts.tokenBeg;
        int lineno, column;

        if (tt == Token.RESERVED && isClassKeyword()) {
            if (inSingleStatementContext) {
                reportError("msg.class.not.statement");
            }
            return classDeclaration();
        }

        switch (tt) {
            case Token.IF:
                return ifStatement();

            case Token.SWITCH:
                return switchStatement();

            case Token.WHILE:
                return whileLoop();

            case Token.DO:
                return doLoop();

            case Token.FOR:
                return hideLoopHeadBindings(forLoop());

            case Token.TRY:
                return tryStatement();

            case Token.THROW:
                pn = throwStatement();
                break;

            case Token.BREAK:
                pn = breakStatement();
                break;

            case Token.CONTINUE:
                pn = continueStatement();
                break;

            case Token.WITH:
                if (this.inUseStrictDirective) {
                    reportError("msg.no.with.strict");
                }
                return withStatement();

            case Token.CONST:
            case Token.VAR:
                consumeToken();
                if (tt == Token.CONST && labelledItem && compilerEnv.isLatestEcmaScript()) {
                    reportError("msg.const.decl.not.in.block");
                }
                lineno = lineNumber();
                column = columnNumber();
                pn = variables(currentToken, ts.tokenBeg, true);
                pn.setLineColumnNumber(lineno, column);
                break;

            case Token.LET:
                boolean letSingle = inSingleStatementContext || labelledItem;
                if (letIsIdentifier(letSingle)) {
                    // an expression statement starting with the identifier let
                    letAsName();
                    lineno = ts.getLineno();
                    column = ts.getTokenColumn();
                    pn = new ExpressionStatement(expr(false), !insideFunctionBody());
                    pn.setLineColumnNumber(lineno, column);
                    break;
                }
                if (letSingle && compilerEnv.isLatestEcmaScript()) {
                    // if (a) let [x] = y: a lexical declaration is not a statement
                    reportError("msg.let.decl.not.in.block");
                }
                pn = letStatement();
                if (pn instanceof VariableDeclaration) break;
                return pn;

            case Token.YIELD:
                if (compilerEnv.isLatestEcmaScript()) {
                    // an expression statement: yield is an AssignmentExpression (yield a, b)
                    lineno = ts.getLineno();
                    column = ts.getTokenColumn();
                    pn = new ExpressionStatement(expr(false), !insideFunctionBody());
                    pn.setLineColumnNumber(lineno, column);
                    break;
                }
            // fall through
            case Token.RETURN:
                pn = returnOrYield(tt, false);
                break;

            case Token.DEBUGGER:
                consumeToken();
                pn = new KeywordLiteral(ts.tokenBeg, ts.tokenEnd - ts.tokenBeg, tt);
                pn.setLineColumnNumber(lineNumber(), columnNumber());
                break;

            case Token.LC:
                return block();

            case Token.ERROR:
                consumeToken();
                return makeErrorNode();

            case Token.SEMI:
                consumeToken();
                pos = ts.tokenBeg;
                pn = new EmptyStatement(pos, ts.tokenEnd - pos);
                pn.setLineColumnNumber(lineNumber(), columnNumber());
                return pn;

            case Token.FUNCTION:
                consumeToken();
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    // ES6: block functions are block-scoped. Annex B hoisting
                    // for non-strict is resolved later in resolveAnnexBFunctions.
                    return function(FunctionNode.FUNCTION_BLOCK_SCOPED);
                }
                // Pre-ES6: legacy behavior
                return function(FunctionNode.FUNCTION_EXPRESSION_STATEMENT);

            case Token.DEFAULT:
                pn = defaultXmlNamespace();
                break;

            case Token.NAME:
                if (isAsyncFunctionStart()) {
                    return asyncFunction(FunctionNode.FUNCTION_BLOCK_SCOPED);
                }
                pn = nameOrLabel();
                if (pn instanceof ExpressionStatement) break;
                return pn; // LabeledStatement
            case Token.COMMENT:
                // Do not consume token here
                pn = scannedComments.get(scannedComments.size() - 1);
                return pn;
            default:
                // Intentionally not calling lineNumber/columnNumber here!
                // We have not consumed any token yet, so the position would be invalid
                lineno = ts.getLineno();
                column = ts.getTokenColumn();
                pn = new ExpressionStatement(expr(false), !insideFunctionBody());
                pn.setLineColumnNumber(lineno, column);
                break;
        }

        autoInsertSemicolon(pn);
        return pn;
    }

    private void autoInsertSemicolon(AstNode pn) throws IOException {
        int ttFlagged = peekFlaggedToken();
        int pos = pn.getPosition();
        switch (ttFlagged & CLEAR_TI_MASK) {
            case Token.SEMI:
                // Consume ';' as a part of expression
                consumeToken();
                // extend the node bounds to include the semicolon.
                pn.setLength(ts.tokenEnd - pos);
                break;
            case Token.ERROR:
            case Token.EOF:
            case Token.RC:
                // Autoinsert ;
                // Token.EOF can have negative length and negative nodeEnd(pn).
                // So, make the end position at least pos+1.
                warnMissingSemi(pos, Math.max(pos + 1, nodeEnd(pn)));
                break;
            default:
                if ((ttFlagged & TI_AFTER_EOL) == 0) {
                    // Report error if no EOL or autoinsert ; otherwise
                    reportError("msg.no.semi.stmt");
                } else {
                    warnMissingSemi(pos, nodeEnd(pn));
                }
                break;
        }
    }

    private IfStatement ifStatement() throws IOException {
        if (currentToken != Token.IF) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, lineno = lineNumber(), elsePos = -1, column = columnNumber();
        IfStatement pn = new IfStatement(pos);
        ConditionData data = condition();
        boolean isES6 = compilerEnv.getLanguageVersion() >= Context.VERSION_ES6;
        boolean savedInSingleStatementContext = inSingleStatementContext;
        inSingleStatementContext = isES6;
        inSingleStatementDeclContext = inSingleStatementContext;
        AstNode ifTrue = parseImplicitBlockIfFunction(pn);
        AstNode ifFalse = null;
        if (matchToken(Token.ELSE, true)) {
            int tt = peekToken();
            if (tt == Token.COMMENT) {
                pn.setElseKeyWordInlineComment(scannedComments.get(scannedComments.size() - 1));
                consumeToken();
            }
            elsePos = ts.tokenBeg - pos;
            inSingleStatementContext = isES6;
            inSingleStatementDeclContext = inSingleStatementContext;
            ifFalse = parseImplicitBlockIfFunction(null);
        }
        inSingleStatementContext = savedInSingleStatementContext;
        inSingleStatementDeclContext = savedInSingleStatementContext;
        int end = getNodeEnd(ifFalse != null ? ifFalse : ifTrue);
        pn.setLength(end - pos);
        pn.setCondition(data.condition);
        pn.setParens(data.lp - pos, data.rp - pos);
        pn.setThenPart(ifTrue);
        pn.setElsePart(ifFalse);
        pn.setElsePosition(elsePos);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    /**
     * B.3.2: If the next token is a bare function declaration (not in braces), wrap it in an
     * implicit block scope so it gets proper block-scoped semantics. Otherwise, parse normally.
     */
    private AstNode parseImplicitBlockIfFunction(AstNode parentForComments) throws IOException {
        if (peekToken() == Token.FUNCTION
                && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                && !inUseStrictDirective) {
            int blockPos = ts.tokenBeg;
            Scope implicitBlock = new Scope(blockPos);
            implicitBlock.setLineColumnNumber(lineNumber(), columnNumber());
            pushScope(implicitBlock);
            boolean saved = inSingleStatementContext;
            boolean savedDecl = inSingleStatementDeclContext;
            inSingleStatementContext = false;
            inSingleStatementDeclContext = false;
            try {
                AstNode stmt =
                        parentForComments != null
                                ? getNextStatementAfterInlineComments(parentForComments)
                                : statement();
                checkStatementBody(stmt, true);
                implicitBlock.addChild(stmt);
                implicitBlock.setLength(getNodeEnd(stmt) - blockPos);
                return implicitBlock;
            } finally {
                popScope();
                inSingleStatementContext = saved;
                inSingleStatementDeclContext = savedDecl;
            }
        }
        AstNode stmt =
                parentForComments != null
                        ? getNextStatementAfterInlineComments(parentForComments)
                        : statement();
        checkStatementBody(stmt, true);
        return stmt;
    }

    private SwitchStatement switchStatement() throws IOException {
        if (currentToken != Token.SWITCH) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;

        SwitchStatement pn = new SwitchStatement(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        pushScope(pn);
        try {
            if (mustMatchToken(Token.LP, "msg.no.paren.switch", true)) pn.setLp(ts.tokenBeg - pos);

            AstNode discriminant = expr(false);
            pn.setExpression(discriminant);
            enterSwitch(pn);

            try {
                if (mustMatchToken(Token.RP, "msg.no.paren.after.switch", true))
                    pn.setRp(ts.tokenBeg - pos);

                mustMatchToken(Token.LC, "msg.no.brace.switch", true);

                boolean hasDefault = false;
                int tt;
                switchLoop:
                for (; ; ) {
                    tt = nextToken();
                    int casePos = ts.tokenBeg;
                    int caseLineno = lineNumber(), caseColumn = columnNumber();
                    AstNode caseExpression = null;
                    switch (tt) {
                        case Token.RC:
                            pn.setLength(ts.tokenEnd - pos);
                            break switchLoop;

                        case Token.CASE:
                            caseExpression = expr(false);
                            mustMatchToken(Token.COLON, "msg.no.colon.case", true);
                            break;

                        case Token.DEFAULT:
                            if (hasDefault) {
                                reportError("msg.double.switch.default");
                            }
                            hasDefault = true;
                            mustMatchToken(Token.COLON, "msg.no.colon.case", true);
                            break;
                        case Token.COMMENT:
                            AstNode n = scannedComments.get(scannedComments.size() - 1);
                            pn.addChild(n);
                            continue switchLoop;
                        default:
                            reportError("msg.bad.switch");
                            break switchLoop;
                    }

                    SwitchCase caseNode = new SwitchCase(casePos);
                    caseNode.setExpression(caseExpression);
                    caseNode.setLength(ts.tokenEnd - pos); // include colon
                    caseNode.setLineColumnNumber(caseLineno, caseColumn);

                    while ((tt = peekToken()) != Token.RC
                            && tt != Token.CASE
                            && tt != Token.DEFAULT
                            && tt != Token.EOF) {
                        if (tt == Token.COMMENT) {
                            Comment inlineComment = scannedComments.get(scannedComments.size() - 1);
                            if (caseNode.getInlineComment() == null
                                    && inlineComment.getLineno() == caseNode.getLineno()) {
                                caseNode.setInlineComment(inlineComment);
                            } else {
                                caseNode.addStatement(inlineComment);
                            }
                            consumeToken();
                            continue;
                        }
                        AstNode nextStmt = statement();
                        caseNode.addStatement(nextStmt); // updates length
                    }
                    pn.addCase(caseNode);
                }
            } finally {
                exitSwitch();
            }
            // the discriminant is evaluated before the block of the cases (14.12.4)
            hideBlockBindings(pn, pn.getExpression(), Collections.emptySet());
            return pn;
        } finally {
            popScope();
        }
    }

    private WhileLoop whileLoop() throws IOException {
        if (currentToken != Token.WHILE) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;
        WhileLoop pn = new WhileLoop(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        enterLoop(pn);
        try {
            ConditionData data = condition();
            pn.setCondition(data.condition);
            pn.setParens(data.lp - pos, data.rp - pos);
            boolean savedInSingleStatementContext = inSingleStatementContext;
            boolean savedInSingleStatementDeclContext = inSingleStatementDeclContext;
            inSingleStatementContext = compilerEnv.getLanguageVersion() >= Context.VERSION_ES6;
            inSingleStatementDeclContext = false;
            AstNode body = getNextStatementAfterInlineComments(pn);
            checkStatementBody(body, false);
            inSingleStatementContext = savedInSingleStatementContext;
            inSingleStatementDeclContext = savedInSingleStatementDeclContext;
            pn.setLength(getNodeEnd(body) - pos);
            restoreRelativeLoopPosition(pn);
            pn.setBody(body);
        } finally {
            exitLoop();
        }
        return pn;
    }

    private DoLoop doLoop() throws IOException {
        if (currentToken != Token.DO) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, end;
        DoLoop pn = new DoLoop(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        enterLoop(pn);
        try {
            boolean savedInSingleStatementContext = inSingleStatementContext;
            boolean savedInSingleStatementDeclContext = inSingleStatementDeclContext;
            inSingleStatementContext = compilerEnv.getLanguageVersion() >= Context.VERSION_ES6;
            inSingleStatementDeclContext = false;
            AstNode body = getNextStatementAfterInlineComments(pn);
            checkStatementBody(body, false);
            inSingleStatementContext = savedInSingleStatementContext;
            inSingleStatementDeclContext = savedInSingleStatementDeclContext;
            mustMatchToken(Token.WHILE, "msg.no.while.do", true);
            pn.setWhilePosition(ts.tokenBeg - pos);
            ConditionData data = condition();
            pn.setCondition(data.condition);
            pn.setParens(data.lp - pos, data.rp - pos);
            end = getNodeEnd(body);
            restoreRelativeLoopPosition(pn);
            pn.setBody(body);
        } finally {
            exitLoop();
        }
        // Always auto-insert semicolon to follow SpiderMonkey:
        // It is required by ECMAScript but is ignored by the rest of
        // world, see bug 238945
        if (matchToken(Token.SEMI, true)) {
            end = ts.tokenEnd;
        }
        pn.setLength(end - pos);
        return pn;
    }

    private int peekUntilNonComment(int tt) throws IOException {
        while (tt == Token.COMMENT) {
            consumeToken();
            tt = peekToken();
        }
        return tt;
    }

    private AstNode getNextStatementAfterInlineComments(AstNode pn) throws IOException {
        AstNode body = statement();
        Comment mergedComment = null;
        StringBuilder sb = null;
        while (Token.COMMENT == body.getType()) {
            if (mergedComment == null) {
                mergedComment = (Comment) body;
                sb = new StringBuilder(mergedComment.getValue());
            } else {
                sb.append('\n').append(((Comment) body).getValue());
                scannedComments.remove(scannedComments.size() - 1);
            }
            body = statement();
        }
        if (mergedComment != null) {
            mergedComment.setValue(sb.toString());
            if (pn != null) {
                pn.setInlineComment(mergedComment);
            } else {
                body.setInlineComment(mergedComment);
            }
        }
        return body;
    }

    private Loop forLoop() throws IOException {
        if (currentToken != Token.FOR) codeBug();
        consumeToken();
        int forPos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        boolean isForEach = false, isForIn = false, isForOf = false, isForAwait = false;
        int eachPos = -1, inPos = -1, lp = -1, rp = -1;
        AstNode init = null; // init is also foo in 'foo in object'
        AstNode cond = null; // cond is also object in 'foo in object'
        AstNode incr = null;
        Loop pn = null;

        Scope tempScope = new Scope();
        pushScope(tempScope); // decide below what AST class to use
        try {
            if (matchToken(Token.AWAIT, true)) {
                // for await (... of ...), in an async function (14.7.5) or a module
                if (isModuleTopLevel()) {
                    moduleInfo.hasTopLevelAwait = true;
                } else if (!inAsyncFunction() || functionOfParams == currentScriptOrFn) {
                    reportError("msg.bad.await");
                }
                isForAwait = true;
            }
            // See if this is a for each () instead of just a for ()
            if (!isForAwait && matchToken(Token.NAME, true)) {
                if ("each".equals(ts.getString())) {
                    isForEach = true;
                    eachPos = ts.tokenBeg - forPos;
                } else {
                    reportError("msg.no.paren.for");
                }
            }

            if (mustMatchToken(Token.LP, "msg.no.paren.for", true)) lp = ts.tokenBeg - forPos;
            int tt = peekToken();
            boolean initIsAsync =
                    tt == Token.NAME && "async".equals(ts.getString()) && !ts.nameHadEscape;

            init = forLoopInit(tt);
            if (matchToken(Token.IN, true)) {
                isForIn = true;
                inPos = ts.tokenBeg - forPos;
                markDestructuring(init);
                cond = expr(false); // object over which we're iterating
            } else if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                    && matchToken(Token.NAME, true)
                    && "of".equals(ts.getString())) {
                checkEscapedKeyword();
                isForOf = true;
                inPos = ts.tokenBeg - forPos;
                markDestructuring(init);
                // object over which we're iterating, an AssignmentExpression
                cond = compilerEnv.isLatestEcmaScript() ? assignExpr() : expr(false);
            } else { // ordinary for-loop
                // For ordinary for loops, destructuring declarations must have initializers
                if (init instanceof VariableDeclaration) {
                    VariableDeclaration varDecl = (VariableDeclaration) init;
                    for (VariableInitializer vi : varDecl.getVariables()) {
                        if (vi.isDestructuring() && vi.getInitializer() == null) {
                            reportError("msg.destruct.assign.no.init");
                        } else if (vi.getInitializer() == null
                                && varDecl.getType() == Token.CONST
                                && compilerEnv.isLatestEcmaScript()) {
                            reportError("msg.const.no.init");
                        }
                    }
                }

                if (init.getIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 0) == 1
                        && compilerEnv.isLatestEcmaScript()) {
                    // {x = 1} is only valid as a pattern
                    reportError("msg.syntax");
                }
                mustMatchToken(Token.SEMI, "msg.no.semi.for", true);
                if (peekToken() == Token.SEMI) {
                    // no loop condition
                    cond = new EmptyExpression(ts.tokenBeg, 1);
                    // We haven't consumed the token, so we need the CURRENT lexer position
                    cond.setLineColumnNumber(ts.getLineno(), ts.getTokenColumn());
                } else {
                    cond = expr(false);
                }

                mustMatchToken(Token.SEMI, "msg.no.semi.for.cond", true);
                int tmpPos = ts.tokenEnd;
                if (peekToken() == Token.RP) {
                    incr = new EmptyExpression(tmpPos, 1);
                    // We haven't consumed the token, so we need the CURRENT lexer position
                    incr.setLineColumnNumber(ts.getLineno(), ts.getTokenColumn());
                } else {
                    incr = expr(false);
                }
            }

            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl", true)) rp = ts.tokenBeg - forPos;

            if (isForIn || isForOf) {
                ForInLoop fis = new ForInLoop(forPos);
                if (init instanceof VariableDeclaration) {
                    VariableDeclaration varDecl = (VariableDeclaration) init;
                    // check that there was only one variable given
                    if (varDecl.getVariables().size() > 1) {
                        reportError("msg.mult.index");
                    }
                    // The loop variable declaration may not have an initializer. The
                    // legacy "for (var x = 1 in obj)" form is still tolerated.
                    for (VariableInitializer vi : varDecl.getVariables()) {
                        if (vi.getInitializer() != null
                                && (isForOf
                                        || varDecl.getType() != Token.VAR
                                        || ((inUseStrictDirective || vi.isDestructuring())
                                                && compilerEnv.isLatestEcmaScript()))) {
                            reportError("msg.for.in.of.init");
                        }
                    }
                }
                if (isForOf && isForEach) {
                    reportError("msg.invalid.for.each");
                }
                if (isForAwait && !isForOf) {
                    reportError("msg.for.await.of");
                }
                if (isForOf
                        && !isForAwait
                        && initIsAsync
                        && init instanceof Name
                        && compilerEnv.isLatestEcmaScript()) {
                    // for (async of ...): [lookahead != async of] (14.7.5)
                    reportError("msg.syntax");
                }
                if (!(init instanceof VariableDeclaration)
                        && compilerEnv.isLatestEcmaScript()
                        && !isValidAssignmentTarget(init, true, false)) {
                    reportError("msg.bad.for.in.lhs");
                }
                fis.setIterator(init);
                fis.setIteratedObject(cond);
                fis.setInPosition(inPos);
                fis.setIsForEach(isForEach);
                fis.setEachPosition(eachPos);
                fis.setIsForOf(isForOf);
                fis.setIsForAwait(isForAwait);
                pn = fis;
            } else {
                if (isForAwait) {
                    reportError("msg.for.await.of");
                }
                ForLoop fl = new ForLoop(forPos);
                fl.setInitializer(init);
                fl.setCondition(cond);
                fl.setIncrement(incr);
                pn = fl;
            }

            // replace temp scope with the new loop object
            currentScope.replaceWith(pn);
            popScope();

            // We have to parse the body -after- creating the loop node,
            // so that the loop node appears in the loopSet, allowing
            // break/continue statements to find the enclosing loop.
            enterLoop(pn);
            try {
                boolean savedInSingleStatementContext = inSingleStatementContext;
                boolean savedInSingleStatementDeclContext = inSingleStatementDeclContext;
                inSingleStatementContext = compilerEnv.getLanguageVersion() >= Context.VERSION_ES6;
                inSingleStatementDeclContext = false;
                AstNode body = getNextStatementAfterInlineComments(pn);
                checkStatementBody(body, false);
                inSingleStatementContext = savedInSingleStatementContext;
                inSingleStatementDeclContext = savedInSingleStatementDeclContext;
                pn.setLength(getNodeEnd(body) - forPos);
                restoreRelativeLoopPosition(pn);
                pn.setBody(body);
            } finally {
                exitLoop();
            }

        } finally {
            if (currentScope == tempScope) {
                popScope();
            }
        }
        pn.setParens(lp, rp);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private AstNode forLoopInit(int tt) throws IOException {
        try {
            inForInit = true; // checked by variables() and relExpr()
            AstNode init = null;
            if (tt == Token.SEMI) {
                init = new EmptyExpression(ts.tokenBeg, 1);
                // We haven't consumed the token, so we need the CURRENT lexer position
                init.setLineColumnNumber(ts.getLineno(), ts.getTokenColumn());
            } else if (tt == Token.LET && letIsIdentifier(false)) {
                // for (let in o), for (let.p of …): let is an identifier
                letAsName();
                init = expr(false);
            } else if (tt == Token.VAR || tt == Token.LET || tt == Token.CONST) {
                consumeToken();
                init = variables(tt, ts.tokenBeg, false);
            } else if ((tt == Token.LC || tt == Token.LB) && compilerEnv.isLatestEcmaScript()) {
                // may be an assignment pattern of a for-in/of: for ({x = 1} of ...)
                boolean saved = inDestructuringAssignment;
                inDestructuringAssignment = true;
                try {
                    init = expr(false);
                } finally {
                    inDestructuringAssignment = saved;
                }
            } else {
                init = expr(false);
            }
            return init;
        } finally {
            inForInit = false;
        }
    }

    private TryStatement tryStatement() throws IOException {
        if (currentToken != Token.TRY) codeBug();
        consumeToken();

        // Pull out JSDoc info and reset it before recursing.
        Comment jsdocNode = getAndResetJsDoc();

        int tryPos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber(), finallyPos = -1;

        TryStatement pn = new TryStatement(tryPos);
        // Hnadled comment here because there should not be try without LC
        int lctt = peekToken();
        while (lctt == Token.COMMENT) {
            Comment commentNode = scannedComments.get(scannedComments.size() - 1);
            pn.setInlineComment(commentNode);
            consumeToken();
            lctt = peekToken();
        }
        if (lctt != Token.LC) {
            reportError("msg.no.brace.try");
        }
        AstNode tryBlock = getNextStatementAfterInlineComments(pn);
        int tryEnd = getNodeEnd(tryBlock);

        List<CatchClause> clauses = null;

        boolean sawDefaultCatch = false;
        int peek = peekToken();
        while (peek == Token.COMMENT) {
            Comment commentNode = scannedComments.get(scannedComments.size() - 1);
            pn.setInlineComment(commentNode);
            consumeToken();
            peek = peekToken();
        }

        boolean previous = hasUndefinedBeenRedefined;
        if (peek == Token.CATCH) {
            while (matchToken(Token.CATCH, true)) {
                int catchLineNum = lineNumber();
                if (sawDefaultCatch) {
                    reportError("msg.catch.unreachable");
                }
                int catchPos = ts.tokenBeg,
                        lp = -1,
                        rp = -1,
                        guardPos = -1,
                        catchLine = lineNumber(),
                        catchColumn = columnNumber();
                AstNode varName = null;
                AstNode catchCond = null;

                switch (peekToken()) {
                    case Token.LP:
                        {
                            matchToken(Token.LP, true);
                            lp = ts.tokenBeg;

                            int tt = peekToken();
                            if (tt == Token.LB || tt == Token.LC) {
                                // Destructuring pattern
                                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                                    varName = destructuringPrimaryExpr();
                                    markDestructuring(varName);
                                } else {
                                    reportError("msg.catch.destructuring.requires.es6");
                                }
                            } else {
                                // Simple identifier
                                if (!matchToken(Token.UNDEFINED, true)) {
                                    mustMatchToken(Token.NAME, "msg.bad.catchcond", true);
                                }

                                varName = createNameNode();
                                Comment jsdocNodeForName = getAndResetJsDoc();
                                if (jsdocNodeForName != null) {
                                    varName.setJsDocNode(jsdocNodeForName);
                                }
                                String varNameString = ((Name) varName).getIdentifier();
                                if ("undefined".equals(varNameString)) {
                                    hasUndefinedBeenRedefined = true;
                                }
                                if (inUseStrictDirective) {
                                    if ("eval".equals(varNameString)
                                            || "arguments".equals(varNameString)) {
                                        reportError("msg.bad.id.strict", varNameString);
                                    }
                                }
                            }

                            // Non-standard extension: we support "catch (e if cond)
                            if (varName instanceof Name && matchToken(Token.IF, true)) {
                                guardPos = ts.tokenBeg;
                                catchCond = expr(false);
                            } else {
                                sawDefaultCatch = true;
                            }

                            if (mustMatchToken(Token.RP, "msg.bad.catchcond", true)) {
                                rp = ts.tokenBeg;
                            }
                            mustMatchToken(Token.LC, "msg.no.brace.catchblock", true);
                        }
                        break;
                    case Token.LC:
                        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                            matchToken(Token.LC, true);
                        } else {
                            reportError("msg.no.paren.catch");
                        }
                        break;
                    default:
                        reportError("msg.no.paren.catch");
                        break;
                }

                Scope catchScope = new Scope(catchPos);
                CatchClause catchNode = new CatchClause(catchPos);
                catchNode.setLineColumnNumber(catchLine, catchColumn);
                if (varName != null
                        && !(varName instanceof Name)
                        && compilerEnv.isLatestEcmaScript()) {
                    List<String> names = new ArrayList<>();
                    collectBoundNames(varName, names);
                    catchScope.setCatchPatternNames(names);
                }
                pushScope(catchScope);
                try {
                    statements(catchScope);
                } finally {
                    hasUndefinedBeenRedefined = previous;
                    popScope();
                }
                if (varName != null && compilerEnv.isLatestEcmaScript()) {
                    checkCatchParameterRedeclaration(varName, catchScope);
                    // the parameter is bound outside the block (14.15.2 CatchClauseEvaluation)
                    List<String> paramNames = new ArrayList<>();
                    collectBoundNames(varName, paramNames);
                    hideBlockBindings(catchScope, varName, paramNames);
                }

                tryEnd = getNodeEnd(catchScope);
                catchNode.setVarName(varName);
                catchNode.setCatchCondition(catchCond);
                catchNode.setBody(catchScope);
                if (guardPos != -1) {
                    catchNode.setIfPosition(guardPos - catchPos);
                }
                catchNode.setParens(lp, rp);

                if (mustMatchToken(Token.RC, "msg.no.brace.after.body", true)) tryEnd = ts.tokenEnd;
                catchNode.setLength(tryEnd - catchPos);
                if (clauses == null) clauses = new ArrayList<>();
                clauses.add(catchNode);
            }
        } else if (peek != Token.FINALLY) {
            mustMatchToken(Token.FINALLY, "msg.try.no.catchfinally", true);
        }

        AstNode finallyBlock = null;
        if (matchToken(Token.FINALLY, true)) {
            finallyPos = ts.tokenBeg;
            finallyBlock = statement();
            tryEnd = getNodeEnd(finallyBlock);
        }

        pn.setLength(tryEnd - tryPos);
        pn.setTryBlock(tryBlock);
        pn.setCatchClauses(clauses);
        pn.setFinallyBlock(finallyBlock);
        if (finallyPos != -1) {
            pn.setFinallyPosition(finallyPos - tryPos);
        }
        pn.setLineColumnNumber(lineno, column);

        if (jsdocNode != null) {
            pn.setJsDocNode(jsdocNode);
        }

        return pn;
    }

    private ThrowStatement throwStatement() throws IOException {
        if (currentToken != Token.THROW) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        if (peekTokenOrEOL() == Token.EOL) {
            // ECMAScript does not allow new lines before throw expression,
            // see bug 256617
            reportError("msg.bad.throw.eol");
        }
        AstNode expr = expr(false);
        ThrowStatement pn = new ThrowStatement(pos, expr);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    // If we match a NAME, consume the token and return the statement
    // with that label.  If the name does not match an existing label,
    // reports an error.  Returns the labeled statement node, or null if
    // the peeked token was not a name.  Side effect:  sets scanner token
    // information for the label identifier (tokenBeg, tokenEnd, etc.)

    private LabeledStatement matchJumpLabelName() throws IOException {
        LabeledStatement label = null;

        if (peekTokenOrEOL() == Token.NAME) {
            consumeToken();
            if (labelSet != null) {
                label = labelSet.get(ts.getString());
            }
            if (label == null) {
                reportError("msg.undef.label");
            }
        }

        return label;
    }

    private BreakStatement breakStatement() throws IOException {
        if (currentToken != Token.BREAK) codeBug();
        consumeToken();
        int lineno = lineNumber(), pos = ts.tokenBeg, end = ts.tokenEnd, column = columnNumber();
        Name breakLabel = null;
        if (peekTokenOrEOL() == Token.NAME) {
            breakLabel = createNameNode();
            end = getNodeEnd(breakLabel);
        }

        // matchJumpLabelName only matches if there is one
        LabeledStatement labels = matchJumpLabelName();
        // always use first label as target
        Jump breakTarget = labels == null ? null : labels.getFirstLabel();

        if (breakTarget == null && breakLabel == null) {
            if (loopAndSwitchSet == null || loopAndSwitchSet.size() == 0) {
                reportError("msg.bad.break", pos, end - pos);
            } else {
                breakTarget = loopAndSwitchSet.get(loopAndSwitchSet.size() - 1);
            }
        }

        if (breakLabel != null) {
            breakLabel.setLineColumnNumber(lineNumber(), columnNumber());
        }

        BreakStatement pn = new BreakStatement(pos, end - pos);
        pn.setBreakLabel(breakLabel);
        // can be null if it's a bad break in error-recovery mode
        if (breakTarget != null) pn.setBreakTarget(breakTarget);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private ContinueStatement continueStatement() throws IOException {
        if (currentToken != Token.CONTINUE) codeBug();
        consumeToken();
        int lineno = lineNumber(), pos = ts.tokenBeg, end = ts.tokenEnd, column = columnNumber();
        Name label = null;
        if (peekTokenOrEOL() == Token.NAME) {
            label = createNameNode();
            end = getNodeEnd(label);
        }

        // matchJumpLabelName only matches if there is one
        LabeledStatement labels = matchJumpLabelName();
        Loop target = null;
        if (labels == null && label == null) {
            if (loopSet == null || loopSet.size() == 0) {
                reportError("msg.continue.outside");
            } else {
                target = loopSet.get(loopSet.size() - 1);
            }
        } else {
            if (labels == null || !(labels.getStatement() instanceof Loop)) {
                reportError("msg.continue.nonloop", pos, end - pos);
            }
            target = labels == null ? null : (Loop) labels.getStatement();
        }

        if (label != null) {
            label.setLineColumnNumber(lineNumber(), columnNumber());
        }

        ContinueStatement pn = new ContinueStatement(pos, end - pos);
        if (target != null) // can be null in error-recovery mode
        pn.setTarget(target);
        pn.setLabel(label);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private WithStatement withStatement() throws IOException {
        if (currentToken != Token.WITH) codeBug();
        consumeToken();

        Comment withComment = getAndResetJsDoc();

        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg, lp = -1, rp = -1;
        if (mustMatchToken(Token.LP, "msg.no.paren.with", true)) lp = ts.tokenBeg;

        AstNode obj = expr(false);

        if (mustMatchToken(Token.RP, "msg.no.paren.after.with", true)) rp = ts.tokenBeg;

        WithStatement pn = new WithStatement(pos);

        boolean previous = hasUndefinedBeenRedefined;
        try {
            hasUndefinedBeenRedefined = true;
            if (peekToken() == Token.RESERVED && isClassKeyword()) {
                reportError("msg.class.not.statement");
            }
            letInSingleStatement = true;
            AstNode body = getNextStatementAfterInlineComments(pn);
            checkStatementBody(body, false);
            letInSingleStatement = false;

            pn.setLength(getNodeEnd(body) - pos);
            pn.setJsDocNode(withComment);
            pn.setExpression(obj);
            pn.setStatement(body);
            pn.setParens(lp, rp);
            pn.setLineColumnNumber(lineno, column);
        } finally {
            hasUndefinedBeenRedefined = previous;
        }

        return pn;
    }

    /**
     * Whether the peeked let token is an identifier (VERSION_ECMASCRIPT, sloppy code): it is a
     * declaration when followed by [, or by an identifier or { (unless a line terminator follows
     * let where only a statement is allowed: if (a) let \n x is the expression let, then x).
     */
    private boolean letIsIdentifier(boolean singleStatement) throws IOException {
        if (!compilerEnv.isLatestEcmaScript() || inUseStrictDirective || peekToken() != Token.LET) {
            return false;
        }
        TokenStream.LookAhead next = ts.lookAhead();
        if (next == null || next.next == '[') {
            return false;
        }
        if (next.startsIdentifier()) {
            if ("in".equals(next.word) || "instanceof".equals(next.word)) {
                return true;
            }
            return singleStatement && next.lineBreak;
        }
        if (next.next == '{') {
            return singleStatement && next.lineBreak;
        }
        return true;
    }

    /** The peeked let token becomes an identifier. */
    private void letAsName() {
        currentToken = Token.NAME;
        currentFlaggedToken = (currentFlaggedToken & ~CLEAR_TI_MASK) | Token.NAME;
        saveNameTokenData(ts.tokenBeg, "let", ts.getLineno(), ts.getTokenColumn());
    }

    private AstNode letStatement() throws IOException {
        if (currentToken != Token.LET) codeBug();
        consumeToken();
        int lineno = lineNumber(), pos = ts.tokenBeg, column = columnNumber();
        AstNode pn;
        if (peekToken() == Token.LP) {
            pn = let(true, pos);
        } else {
            pn = variables(Token.LET, pos, true); // else, e.g.: let x=6, y=7;
        }
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    /**
     * Returns whether or not the bits in the mask have changed to all set.
     *
     * @param before bits before change
     * @param after bits after change
     * @param mask mask for bits
     * @return {@code true} if all the bits in the mask are set in "after" but not in "before"
     */
    private static final boolean nowAllSet(int before, int after, int mask) {
        return ((before & mask) != mask) && ((after & mask) == mask);
    }

    private AstNode returnOrYield(int tt, boolean exprContext) throws IOException {
        if (!insideFunctionBody() || (tt == Token.RETURN && inStaticBlock)) {
            reportError(tt == Token.RETURN ? "msg.bad.return" : "msg.bad.yield");
        } else if (tt == Token.YIELD
                && compilerEnv.isLatestEcmaScript()
                && (!(currentScriptOrFn instanceof FunctionNode
                                && ((FunctionNode) currentScriptOrFn).isGeneratorFunction())
                        || functionOfParams == currentScriptOrFn)) {
            // a yield expression only in the body of a generator; in strict code, yield is
            // reserved elsewhere
            reportError("msg.bad.yield");
        }
        consumeToken();
        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg, end = ts.tokenEnd;

        boolean yieldStar = false;
        // under VERSION_ECMASCRIPT, yield and * are on the same line
        if ((tt == Token.YIELD)
                && (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6)
                && ((compilerEnv.isLatestEcmaScript() ? peekTokenOrEOL() : peekToken())
                        == Token.MUL)) {
            yieldStar = true;
            consumeToken();
        }

        AstNode e = null;
        // This is ugly, but we don't want to require a semicolon.
        boolean es6Yield = tt == Token.YIELD && compilerEnv.isLatestEcmaScript();
        // yield* requires an operand, which may be on the next line (under VERSION_ECMASCRIPT)
        switch (yieldStar && es6Yield ? peekToken() : peekTokenOrEOL()) {
            case Token.SEMI:
            case Token.RC:
            case Token.RB:
            case Token.RP:
            case Token.EOF:
            case Token.EOL:
            case Token.ERROR:
                break;
            case Token.COMMA:
            case Token.COLON:
                if (es6Yield && !yieldStar) {
                    // yield without operand: { ...yield, a }, c ? yield : d
                    break;
                }
                e = expr(false);
                end = getNodeEnd(e);
                break;
            case Token.YIELD:
                if (compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
                    // Take extra care to preserve language compatibility
                    break;
                }
            // fallthrough
            default:
                // the operand of yield is an AssignmentExpression: [yield 1, 2] has two elements
                e = es6Yield ? assignExpr() : expr(false);
                end = getNodeEnd(e);
        }

        int before = endFlags;
        AstNode ret;

        if (tt == Token.RETURN && e != null && inAsyncGenerator()) {
            // return in an async generator awaits its operand (14.10.1)
            Yield await = new Yield(e.getPosition(), e.getLength(), e, false);
            await.setIsAwait();
            await.setIsAwaitRequest();
            e = await;
        }
        if (tt == Token.RETURN) {
            endFlags |= e == null ? Node.END_RETURNS : Node.END_RETURNS_VALUE;
            ret = new ReturnStatement(pos, end - pos, e);

            // see if we need a strict mode warning
            if (nowAllSet(before, endFlags, Node.END_RETURNS | Node.END_RETURNS_VALUE))
                addStrictWarning("msg.return.inconsistent", "", pos, end - pos);
        } else {
            if (!insideFunctionBody()) reportError("msg.bad.yield");
            endFlags |= Node.END_YIELDS;
            ret = new Yield(pos, end - pos, e, yieldStar);
            if (yieldStar && inAsyncGenerator()) {
                // the delegation of an async generator is driven by NativeAsyncGenerator
                ((Yield) ret).setIsAwaitRequest();
            }
            setRequiresActivation();
            setIsGenerator();
            if (!exprContext) {
                ret.setLineColumnNumber(lineno, column);
                ret = new ExpressionStatement(ret);
            }
        }

        // see if we are mixing yields and value returns.
        if (insideFunctionBody()
                && nowAllSet(before, endFlags, Node.END_YIELDS | Node.END_RETURNS_VALUE)) {
            FunctionNode fn = (FunctionNode) currentScriptOrFn;
            if (!fn.isES6Generator()) {
                Name name = ((FunctionNode) currentScriptOrFn).getFunctionName();
                if (name == null || name.length() == 0) {
                    addError("msg.anon.generator.returns", "");
                } else {
                    addError("msg.generator.returns", name.getIdentifier());
                }
            }
        }

        ret.setLineColumnNumber(lineno, column);
        return ret;
    }

    private AstNode block() throws IOException {
        if (currentToken != Token.LC) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;
        Scope block = new Scope(pos);
        block.setLineColumnNumber(lineNumber(), columnNumber());
        pushScope(block);
        boolean savedInSingleStatementContext = inSingleStatementContext;
        boolean savedInSingleStatementDeclContext = inSingleStatementDeclContext;
        inSingleStatementContext = false;
        inSingleStatementDeclContext = false;
        try {
            statements(block);
            mustMatchToken(Token.RC, "msg.no.brace.block", true);
            block.setLength(ts.tokenEnd - pos);
            return block;
        } finally {
            popScope();
            inSingleStatementContext = savedInSingleStatementContext;
            inSingleStatementDeclContext = savedInSingleStatementDeclContext;
        }
    }

    private AstNode defaultXmlNamespace() throws IOException {
        if (currentToken != Token.DEFAULT) codeBug();
        consumeToken();
        mustHaveXML();
        setRequiresActivation();
        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg;

        if (!(matchToken(Token.NAME, true) && "xml".equals(ts.getString()))) {
            reportError("msg.bad.namespace");
        }
        if (!(matchToken(Token.NAME, true) && "namespace".equals(ts.getString()))) {
            reportError("msg.bad.namespace");
        }
        if (!matchToken(Token.ASSIGN, true)) {
            reportError("msg.bad.namespace");
        }

        AstNode e = expr(false);
        UnaryExpression dxmln = new UnaryExpression(pos, getNodeEnd(e) - pos);
        dxmln.setOperator(Token.DEFAULTNAMESPACE);
        dxmln.setOperand(e);
        dxmln.setLineColumnNumber(lineno, column);

        ExpressionStatement es = new ExpressionStatement(dxmln, true);
        return es;
    }

    private void recordLabel(Label label, LabeledStatement bundle) throws IOException {
        // current token should be colon that primaryExpr left untouched
        if (peekToken() != Token.COLON) codeBug();
        consumeToken();
        String name = label.getName();
        if (labelSet == null) {
            labelSet = new HashMap<>();
        } else {
            LabeledStatement ls = labelSet.get(name);
            if (ls != null) {
                if (compilerEnv.isIdeMode()) {
                    Label dup = ls.getLabelByName(name);
                    reportError("msg.dup.label", dup.getAbsolutePosition(), dup.getLength());
                }
                reportError("msg.dup.label", label.getPosition(), label.getLength());
            }
        }
        bundle.addLabel(label);
        labelSet.put(name, bundle);
    }

    /**
     * Found a name in a statement context. If it's a label, we gather up any following labels and
     * the next non-label statement into a {@link LabeledStatement} "bundle" and return that.
     * Otherwise we parse an expression and return it wrapped in an {@link ExpressionStatement}.
     */
    private AstNode nameOrLabel() throws IOException {
        if (currentToken != Token.NAME) throw codeBug();
        int pos = ts.tokenBeg;

        // set check for label and call down to primaryExpr
        currentFlaggedToken |= TI_CHECK_LABEL;
        AstNode expr = expr(false);

        if (expr.getType() != Token.LABEL) {
            AstNode n = new ExpressionStatement(expr, !insideFunctionBody());
            n.setLineColumnNumber(expr.getLineno(), expr.getColumn());
            return n;
        }

        LabeledStatement bundle = new LabeledStatement(pos);
        recordLabel((Label) expr, bundle);
        bundle.setLineColumnNumber(expr.getLineno(), expr.getColumn());
        // look for more labels
        AstNode stmt = null;
        while (peekToken() == Token.NAME && !isAsyncFunctionStart()) {
            currentFlaggedToken |= TI_CHECK_LABEL;
            expr = expr(false);
            if (expr.getType() != Token.LABEL) {
                stmt = new ExpressionStatement(expr, !insideFunctionBody());
                autoInsertSemicolon(stmt);
                break;
            }
            recordLabel((Label) expr, bundle);
        }

        // no more labels; now parse the labeled statement
        try {
            currentLabel = bundle;
            if (stmt == null) {
                if (peekToken() == Token.RESERVED && isClassKeyword()) {
                    reportError("msg.class.not.statement");
                }
                int stt = peekToken();
                if (compilerEnv.isLatestEcmaScript()
                        && stt != Token.FOR
                        && stt != Token.WHILE
                        && stt != Token.DO
                        && stt != Token.COMMENT) {
                    // continue label needs a labelled iteration statement
                    currentLabel = null;
                }
                letInSingleStatement = true;
                stmt = statementHelper();
                letInSingleStatement = false;
                if (stmt instanceof FunctionNode
                        && compilerEnv.isLatestEcmaScript()
                        && (inUseStrictDirective || ((FunctionNode) stmt).isGenerator())) {
                    reportError("msg.function.decl.not.in.block");
                }
                int ntt = peekToken();
                if (ntt == Token.COMMENT
                        && stmt.getLineno()
                                == scannedComments.get(scannedComments.size() - 1).getLineno()) {
                    stmt.setInlineComment(scannedComments.get(scannedComments.size() - 1));
                    consumeToken();
                }
            }
        } finally {
            currentLabel = null;
            // remove the labels for this statement from the global set
            for (Label lb : bundle.getLabels()) {
                labelSet.remove(lb.getName());
            }
        }

        // If stmt has parent assigned its position already is relative
        // (See bug #710225)
        bundle.setLength(stmt.getParent() == null ? getNodeEnd(stmt) - pos : getNodeEnd(stmt));
        bundle.setStatement(stmt);
        return bundle;
    }

    /**
     * Parse a 'var' or 'const' statement, or a 'var' init list in a for statement.
     *
     * @param declType A token value: either VAR, CONST, or LET depending on context.
     * @param pos the position where the node should start. It's sometimes the var/const/let
     *     keyword, and other times the beginning of the first token in the first variable
     *     declaration.
     * @return the parsed variable list
     */
    private VariableDeclaration variables(int declType, int pos, boolean isStatement)
            throws IOException {
        int end;
        VariableDeclaration pn = new VariableDeclaration(pos);
        pn.setType(declType);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        Comment varjsdocNode = getAndResetJsDoc();
        if (varjsdocNode != null) {
            pn.setJsDocNode(varjsdocNode);
        }
        // Example:
        // var foo = {a: 1, b: 2}, bar = [3, 4];
        // var {b: s2, a: s1} = foo, x = 6, y, [s3, s4] = bar;
        for (; ; ) {
            AstNode destructuring = null;
            Name name = null;
            int tt = peekToken(), kidPos = ts.tokenBeg;
            end = ts.tokenEnd;

            if (tt == Token.LB || tt == Token.LC) {
                // Destructuring assignment, e.g., var [a,b] = ...

                // TODO: support default values inside destructured assignment
                // eg: for (let { x = 3 } = {}) ...
                destructuring = destructuringPrimaryExpr();
                end = getNodeEnd(destructuring);

                if (!(destructuring instanceof DestructuringForm))
                    reportError("msg.bad.assign.left", kidPos, end - kidPos);
                markDestructuring(destructuring);
            } else {
                // Simple variable name
                if (tt == Token.UNDEFINED) {
                    consumeToken();
                } else if (tt == Token.LET
                        && declType == Token.VAR
                        && compilerEnv.isLatestEcmaScript()
                        && !inUseStrictDirective) {
                    // var let: let is an identifier in sloppy code
                    letAsName();
                    consumeToken();
                } else {
                    mustMatchToken(Token.NAME, "msg.bad.var", true);
                }
                name = createNameNode();
                name.setLineColumnNumber(lineNumber(), columnNumber());
                if (inUseStrictDirective) {
                    String id = ts.getString();
                    if ("eval".equals(id) || "arguments".equals(ts.getString())) {
                        reportError("msg.bad.id.strict", id);
                    }
                }
                defineSymbol(declType, ts.getString(), inForInit);
            }

            int lineno = lineNumber(), column = columnNumber();

            Comment jsdocNode = getAndResetJsDoc();

            AstNode init = null;
            if (matchToken(Token.ASSIGN, true)) {
                init = assignExpr();
                end = getNodeEnd(init);
            } else if (declType == Token.CONST && !inForInit && compilerEnv.isLatestEcmaScript()) {
                reportError("msg.const.no.init");
            }

            VariableInitializer vi = new VariableInitializer(kidPos, end - kidPos);
            if (destructuring != null) {
                if (init == null && !inForInit) {
                    reportError("msg.destruct.assign.no.init");
                }
                vi.setTarget(destructuring);
            } else {
                vi.setTarget(name);
            }
            vi.setInitializer(init);
            vi.setType(declType);
            vi.setJsDocNode(jsdocNode);
            vi.setLineColumnNumber(lineno, column);
            pn.addVariable(vi);
            if (name != null && (declType == Token.LET || declType == Token.CONST)) {
                // the temporal dead zone of the binding ends with its initializer
                Scope defining = currentScope.getDefiningScope(name.getIdentifier());
                Symbol symbol = defining == null ? null : defining.getSymbol(name.getIdentifier());
                if (symbol != null) {
                    symbol.setNode(vi);
                }
            }

            if (!matchToken(Token.COMMA, true)) break;
        }
        pn.setLength(end - pos);
        pn.setIsStatement(isStatement);
        return pn;
    }

    // have to pass in 'let' kwd position to compute kid offsets properly
    private AstNode let(boolean isStatement, int pos) throws IOException {
        LetNode pn = new LetNode(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        if (mustMatchToken(Token.LP, "msg.no.paren.after.let", true)) pn.setLp(ts.tokenBeg - pos);
        pushScope(pn);
        try {
            VariableDeclaration vars = variables(Token.LET, ts.tokenBeg, isStatement);
            pn.setVariables(vars);
            if (mustMatchToken(Token.RP, "msg.no.paren.let", true)) {
                pn.setRp(ts.tokenBeg - pos);
            }
            if (isStatement && peekToken() == Token.LC) {
                // let statement
                consumeToken();
                int beg = ts.tokenBeg; // position stmt at LC
                AstNode stmt = statements();
                mustMatchToken(Token.RC, "msg.no.curly.let", true);
                stmt.setLength(ts.tokenEnd - beg);
                pn.setLength(ts.tokenEnd - pos);
                pn.setBody(stmt);
                pn.setType(Token.LET);
            } else {
                // let expression
                AstNode expr = expr(false);
                pn.setLength(getNodeEnd(expr) - pos);
                pn.setBody(expr);
                if (isStatement) {
                    // let expression in statement context
                    ExpressionStatement es = new ExpressionStatement(pn, !insideFunctionBody());
                    es.setLineColumnNumber(pn.getLineno(), pn.getColumn());
                    return es;
                }
            }
        } finally {
            popScope();
        }
        return pn;
    }

    void defineSymbol(int declType, String name) {
        defineSymbol(Symbol.Type.fromToken(declType), name, false);
    }

    void defineSymbol(Symbol.Type declType, String name) {
        defineSymbol(declType, name, false);
    }

    void defineSymbol(int declType, String name, boolean ignoreNotInBlock) {
        defineSymbol(Symbol.Type.fromToken(declType), name, ignoreNotInBlock);
    }

    void defineSymbol(Symbol.Type declType, String name, boolean ignoreNotInBlock) {
        if ((declType == Symbol.Type.VAR || declType == Symbol.Type.FUNCTION_VAR)
                && currentScriptOrFn instanceof FunctionNode
                && ((FunctionNode) currentScriptOrFn).getSplitOwner() != null) {
            // a var that IRFactory declares in a chunk of a split function is the function's
            ScriptNode owner = ((FunctionNode) currentScriptOrFn).getSplitOwner();
            if (owner.getSymbol(name) == null) {
                owner.putSymbol(new Symbol(declType, name));
            }
            return;
        }
        boolean isES6 = compilerEnv.getLanguageVersion() >= Context.VERSION_ES6;

        if (isES6) {
            defineSymbolES6(declType, name, ignoreNotInBlock);
        } else {
            defineSymbolLegacy(declType, name, ignoreNotInBlock);
        }
    }

    void defineSymbolLegacy(Symbol.Type declType, String name, boolean ignoreNotInBlock) {
        if (name == null) {
            if (compilerEnv.isIdeMode()) { // be robust in IDE-mode
                return;
            }
            codeBug();
        } else if ("undefined".equals(name)) {
            hasUndefinedBeenRedefined = true;
        }
        Scope definingScope = currentScope.getDefiningScope(name);
        Symbol symbol = definingScope != null ? definingScope.getSymbol(name) : null;
        Symbol.Type symDeclType = symbol != null ? symbol.getDeclType() : null;
        if (symbol != null
                && (symDeclType == Symbol.Type.CONST
                        || declType == Symbol.Type.CONST
                        || (definingScope == currentScope && symDeclType == Symbol.Type.LET))) {
            addError(
                    switch (symDeclType) {
                        case CONST -> "msg.const.redecl";
                        case LET -> "msg.let.redecl";
                        case VAR -> "msg.var.redecl";
                        case FUNCTION_VAR, FUNCTION_LET -> "msg.fn.redecl";
                        case LP -> "msg.parm.redecl";
                    },
                    name);
            return;
        }
        switch (declType) {
            case LET:
            case FUNCTION_LET:
                if (!ignoreNotInBlock
                        && ((currentScope.getType() == Token.IF) || currentScope instanceof Loop)) {
                    addError("msg.let.decl.not.in.block");
                    return;
                }
                currentScope.putSymbol(new Symbol(declType, name));
                return;

            case VAR:
            case CONST:
            case FUNCTION_VAR:
                if (symbol != null) {
                    if (symDeclType == Symbol.Type.VAR) addStrictWarning("msg.var.redecl", name);
                    else if (symDeclType == Symbol.Type.LP) {
                        addStrictWarning("msg.var.hides.arg", name);
                    }
                } else {
                    currentScriptOrFn.putSymbol(new Symbol(declType, name));
                }
                return;

            case LP:
                if (symbol != null) {
                    // must be duplicate parameter. Second parameter hides the
                    // first, so go ahead and add the second parameter
                    addWarning("msg.dup.parms", name);
                }
                currentScriptOrFn.putSymbol(new Symbol(declType, name));
                return;

            default:
                throw codeBug();
        }
    }

    void defineSymbolES6(Symbol.Type declType, String name, boolean ignoreNotInBlock) {
        if (name == null) {
            if (compilerEnv.isIdeMode()) { // be robust in IDE-mode
                return;
            }
            codeBug();
        } else if ("undefined".equals(name)) {
            hasUndefinedBeenRedefined = true;
        }
        Scope definingScope = currentScope.getDefiningScope(name);
        Symbol symbol = definingScope != null ? definingScope.getSymbol(name) : null;
        Symbol varSymbol = currentScope.getVarSymbol(name);
        Symbol.Type symDeclType = symbol != null ? symbol.getDeclType() : null;
        if (!isValidES6Redeclaration(
                declType, symDeclType, symbol, varSymbol, currentScope, definingScope)) {
            addError(
                    switch (symDeclType) {
                        case CONST -> "msg.const.redecl";
                        case LET -> "msg.let.redecl";
                        case VAR -> "msg.var.redecl";
                        case FUNCTION_VAR, FUNCTION_LET -> "msg.fn.redecl";
                        case LP -> "msg.parm.redecl";
                    },
                    name);
            return;
        }
        switch (declType) {
            case LET:
            case FUNCTION_LET:
                if (!ignoreNotInBlock
                        && (inSingleStatementContext || currentScope instanceof Loop)) {
                    addError("msg.let.decl.not.in.block");
                    return;
                }
                var sym = new Symbol(declType, name);
                currentScope.putSymbol(sym);
                return;

            case CONST:
                if (!ignoreNotInBlock
                        && (inSingleStatementContext || currentScope instanceof Loop)) {
                    addError("msg.const.decl.not.in.block");
                    return;
                }
                currentScope.putSymbol(new Symbol(declType, name));
                return;
            case FUNCTION_VAR:
                if (!ignoreNotInBlock
                        && !inSingleStatementDeclContext
                        && (inSingleStatementContext || currentScope instanceof Loop)) {
                    addError("msg.function.decl.not.in.block");
                    return;
                }
            // Fall through for FUNCTION_VAR case.
            case VAR:
                if (symbol != null) {
                    if (symDeclType == Symbol.Type.VAR) addStrictWarning("msg.var.redecl", name);
                    else if (symDeclType == Symbol.Type.LP) {
                        addStrictWarning("msg.var.hides.arg", name);
                    }
                } else {
                    varSymbol = new Symbol(declType, name);
                    varSymbol.setDeclaredScope(currentScope);
                    currentScriptOrFn.putSymbol(varSymbol);
                    currentScriptOrFn.putVarSymbol(varSymbol);
                    // Record in every scope from the declaration site up to and
                    // including the enclosing function/script so each intermediate
                    // block scope knows which var-hoisted names originate within it.
                    for (Scope s = currentScope; s != currentScriptOrFn; s = s.getParentScope()) {
                        s.putVarSymbol(varSymbol);
                    }
                }
                return;

            case LP:
                if (symbol != null) {
                    // must be duplicate parameter. Second parameter hides the
                    // first, so go ahead and add the second parameter
                    addWarning("msg.dup.parms", name);
                }
                currentScriptOrFn.putSymbol(new Symbol(declType, name));
                return;

            default:
                throw codeBug();
        }
    }

    private boolean isValidES6Redeclaration(
            Symbol.Type newDeclType,
            Symbol.Type oldDeclType,
            Symbol symbol,
            Symbol varSymbol,
            Scope currentScope,
            Scope definingScope) {
        return isSimpleRedefinition(
                        newDeclType, oldDeclType, symbol, varSymbol, currentScope, definingScope)
                || isAnnexBFunctionRedeclaration(
                        newDeclType, oldDeclType, symbol, varSymbol, currentScope, definingScope)
                || (!maskingLexicalDefinition(
                                newDeclType,
                                oldDeclType,
                                symbol,
                                varSymbol,
                                currentScope,
                                definingScope)
                        && !maskingVarDefinition(
                                newDeclType,
                                oldDeclType,
                                symbol,
                                varSymbol,
                                currentScope,
                                definingScope));
    }

    private boolean isAnnexBFunctionRedeclaration(
            Symbol.Type newDeclType,
            Symbol.Type oldDeclType,
            Symbol symbol,
            Symbol varSymbol,
            Scope currentScope,
            Scope definingScope) {
        return (!inUseStrictDirective
                && (newDeclType == Symbol.Type.FUNCTION_LET
                        || newDeclType == Symbol.Type.FUNCTION_VAR)
                && (oldDeclType == Symbol.Type.FUNCTION_LET
                        || oldDeclType == Symbol.Type.FUNCTION_VAR));
    }

    private boolean isSimpleRedefinition(
            Symbol.Type newDeclType,
            Symbol.Type oldDeclType,
            Symbol symbol,
            Symbol varSymbol,
            Scope currentScope,
            Scope definingScope) {

        return symbol == null
                ? false
                : (isVarRedeclaration(newDeclType, symbol)
                        || isNonLexicalRedeclaration(
                                newDeclType, oldDeclType, symbol, currentScope));
    }

    private boolean isVarRedeclaration(Symbol.Type newDeclType, Symbol symbol) {
        return (symbol.getDeclType() == Symbol.Type.VAR || symbol.getDeclType() == Symbol.Type.LP)
                && newDeclType == Symbol.Type.VAR;
    }

    private boolean isNonLexicalRedeclaration(
            Symbol.Type newDeclType, Symbol.Type oldDeclType, Symbol symbol, Scope currentScope) {
        return (((currentScope == symbol.getDeclaredScope()
                                && oldDeclType != Symbol.Type.FUNCTION_VAR
                                && newDeclType != Symbol.Type.FUNCTION_VAR)
                        || currentScope == currentScriptOrFn)
                && !Symbol.isDeclTypeLexical(oldDeclType)
                && !Symbol.isDeclTypeLexical(newDeclType));
    }

    private boolean maskingLexicalDefinition(
            Symbol.Type newDeclType,
            Symbol.Type oldDeclType,
            Symbol symbol,
            Symbol varSymbol,
            Scope currentScope,
            Scope definingScope) {
        return (currentScope == definingScope && Symbol.isDeclTypeLexical(oldDeclType))
                || (oldDeclType == Symbol.Type.LP
                        && (newDeclType == Symbol.Type.LET || newDeclType == Symbol.Type.CONST)
                        && currentScope == currentScriptOrFn
                        && compilerEnv.isLatestEcmaScript())
                || ((symbol != null
                                && symbol.isDeclTypeLexical()
                                && !Symbol.isDeclTypeLexical(newDeclType))
                        || (varSymbol != null && Symbol.isDeclTypeLexical(newDeclType)));
    }

    private boolean maskingVarDefinition(
            Symbol.Type newDeclType,
            Symbol.Type oldDeclType,
            Symbol symbol,
            Symbol varSymbol,
            Scope currentScope,
            Scope definingScope) {
        if (currentScope != currentScriptOrFn && varSymbol != null) {
            return true;
        }
        return (!Symbol.isDeclTypeLexical(newDeclType)
                        && symbol != null
                        && !symbol.isDeclTypeLexical())
                && newDeclType != oldDeclType;
    }

    private AstNode expr(boolean allowTrailingComma) throws IOException {
        AstNode pn = assignExpr();
        int pos = pn.getPosition();
        while (matchToken(Token.COMMA, true)) {
            int opPos = ts.tokenBeg;
            if (compilerEnv.isStrictMode() && !pn.hasSideEffects())
                addStrictWarning("msg.no.side.effects", "", pos, nodeEnd(pn) - pos);
            if (peekToken() == Token.YIELD && !compilerEnv.isLatestEcmaScript()) {
                // JavaScript 1.7 rule; under VERSION_ECMASCRIPT, yield is an AssignmentExpression
                reportError("msg.yield.parenthesized");
            }
            if (allowTrailingComma && peekToken() == Token.RP) {
                pn.putIntProp(Node.TRAILING_COMMA, 1);
                return pn;
            }
            pn = new InfixExpression(Token.COMMA, pn, assignExpr(), opPos);
        }
        return pn;
    }

    private AstNode assignExpr() throws IOException {
        int tt = peekToken();
        if (tt == Token.YIELD) {
            return returnOrYield(tt, true);
        }

        // Intentionally not calling lineNumber/columnNumber here!
        // We have not consumed any token yet, so the position would be invalid
        int startLine = ts.lineno, startColumn = ts.getTokenColumn();

        AstNode pn = condExpr();
        boolean hasEOL = false;
        tt = peekTokenOrEOL();
        if (tt == Token.EOL) {
            hasEOL = true;
            tt = peekToken();
        }
        if (Token.FIRST_ASSIGN <= tt && tt <= Token.LAST_ASSIGN) {
            consumeToken();

            // Pull out JSDoc info and reset it before recursing.
            Comment jsdocNode = getAndResetJsDoc();

            markDestructuring(pn);
            int opPos = ts.tokenBeg;
            if (compilerEnv.isLatestEcmaScript()
                    ? !isValidAssignmentTarget(pn, tt == Token.ASSIGN, isLogicalAssignment(tt))
                    : isNotValidSimpleAssignmentTarget(pn))
                reportError("msg.syntax.invalid.assignment.lhs");
            if (inUseStrictDirective && pn instanceof Name && compilerEnv.isLatestEcmaScript()) {
                String id = ((Name) pn).getIdentifier();
                if ("eval".equals(id) || "arguments".equals(id)) {
                    reportError("msg.bad.id.strict", id);
                }
            }

            pn = new Assignment(tt, pn, assignExpr(), opPos);

            if (jsdocNode != null) {
                pn.setJsDocNode(jsdocNode);
            }
        } else if (tt == Token.SEMI) {
            // This may be dead code added intentionally, for JSDoc purposes.
            // For example: /** @type Number */ C.prototype.x;
            if (currentJsDocComment != null) {
                pn.setJsDocNode(getAndResetJsDoc());
            }
        } else if (!hasEOL && tt == Token.ARROW) {
            consumeToken();
            if (pn instanceof FunctionCall
                    && asyncCallTarget != null
                    && ((FunctionCall) pn).getTarget() == asyncCallTarget) {
                asyncCallTarget = null;
                functionIsAsync = true;
                pn =
                        arrowFunction(
                                asyncArrowParams((FunctionCall) pn),
                                startLine,
                                startColumn,
                                pn.getPosition());
            } else {
                pn = arrowFunction(pn, startLine, startColumn, -1);
            }
        } else if (pn.getIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 0) == 1
                && !inDestructuringAssignment) {
            if (inParenthesizedCover && compilerEnv.isLatestEcmaScript()) {
                pendingCoverInitializedNames++;
            } else {
                reportError("msg.syntax");
            }
        }
        return pn;
    }

    /**
     * The body of an if, a with or a loop cannot be a labelled function or a generator, and only an
     * if (Annex B.3.3) can have a plain function declaration.
     */
    private void checkStatementBody(AstNode body, boolean isIf) {
        if (!compilerEnv.isLatestEcmaScript()) {
            return;
        }
        AstNode stmt = body;
        boolean labelled = false;
        while (stmt instanceof LabeledStatement) {
            stmt = ((LabeledStatement) stmt).getStatement();
            labelled = true;
        }
        if (stmt instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) stmt;
            int type = fn.getFunctionType();
            if ((type == FunctionNode.FUNCTION_STATEMENT
                            || type == FunctionNode.FUNCTION_BLOCK_SCOPED)
                    && (labelled || !isIf || fn.isGenerator())) {
                reportError("msg.function.decl.not.in.block");
            }
        }
    }

    /**
     * A literal containing a CoverInitializedName ({x = 1}) must become an assignment pattern: it
     * can only be followed by =, by the end of an enclosing literal, parameter list or for head.
     */
    private AstNode checkCoverLiteral(AstNode literal) throws IOException {
        if (literal.getIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 0) == 1
                && compilerEnv.isLatestEcmaScript()) {
            int tt = peekToken();
            if (!(tt == Token.ASSIGN
                    || tt == Token.COMMA
                    || tt == Token.RB
                    || tt == Token.RC
                    || tt == Token.RP
                    || tt == Token.IN
                    || (tt == Token.NAME && "of".equals(ts.getString())))) {
                reportError("msg.syntax");
            }
        }
        return literal;
    }

    /** new.target needs a function that is not an arrow function (an arrow uses the outer one). */
    private boolean insideNonArrowFunction() {
        return nonArrowFunctionDepth > 0;
    }

    /** A keyword (or the {@code target} of {@code new.target}) cannot contain escapes. */
    private void checkEscapedKeyword() {
        if (ts.nameHadEscape && compilerEnv.isLatestEcmaScript()) {
            reportError("msg.invalid.escape");
        }
    }

    private static boolean isLogicalAssignment(int tt) {
        return tt == Token.ASSIGN_LOGICAL_AND
                || tt == Token.ASSIGN_LOGICAL_OR
                || tt == Token.ASSIGN_NULLISH;
    }

    /**
     * AssignmentTargetType (ECMA-262 13.15.1): false when the target is an early error. A call is a
     * target only in sloppy code, outside logical assignment (web compatibility).
     */
    private boolean isValidAssignmentTarget(AstNode pn, boolean allowPattern, boolean logical) {
        if (pn instanceof ParenthesizedExpression) {
            return isValidAssignmentTarget(
                    ((ParenthesizedExpression) pn).getExpression(), false, logical);
        }
        if (pn instanceof Name || pn instanceof XmlRef || pn instanceof XmlMemberGet) {
            return true;
        }
        if (pn instanceof ArrayLiteral || pn instanceof ObjectLiteral) {
            return allowPattern;
        }
        if (pn instanceof PropertyGet || pn instanceof ElementGet) {
            return !isInOptionalChain(pn);
        }
        if (pn.getClass() == FunctionCall.class) {
            return !inUseStrictDirective && !logical && !isInOptionalChain(pn);
        }
        return pn.getType() == Token.GET_REF || pn.getType() == Token.UNDEFINED;
    }

    private static boolean isInOptionalChain(AstNode pn) {
        for (; ; ) {
            if (pn.getType() == Token.QUESTION_DOT) return true;
            if (pn instanceof FunctionCall) {
                if (((FunctionCall) pn).isOptionalCall()) return true;
                pn = ((FunctionCall) pn).getTarget();
            } else if (pn instanceof PropertyGet) {
                pn = ((PropertyGet) pn).getTarget();
            } else if (pn instanceof ElementGet) {
                pn = ((ElementGet) pn).getTarget();
            } else {
                return false;
            }
        }
    }

    private static boolean isNotValidSimpleAssignmentTarget(AstNode pn) {
        if (pn.getType() == Token.GETPROP)
            return isNotValidSimpleAssignmentTarget(((PropertyGet) pn).getLeft());
        return pn.getType() == Token.QUESTION_DOT;
    }

    private AstNode condExpr() throws IOException {
        AstNode pn = nullishCoalescingExpr();
        if (matchToken(Token.HOOK, true)) {
            int qmarkPos = ts.tokenBeg, colonPos = -1;
            /*
             * Always accept the 'in' operator in the middle clause of a ternary,
             * where it's unambiguous, even if we might be parsing the init of a
             * for statement.
             */
            boolean wasInForInit = inForInit;
            inForInit = false;
            AstNode ifTrue;
            try {
                ifTrue = assignExpr();
            } finally {
                inForInit = wasInForInit;
            }
            if (mustMatchToken(Token.COLON, "msg.no.colon.cond", true)) colonPos = ts.tokenBeg;
            AstNode ifFalse = assignExpr();
            int beg = pn.getPosition(), len = getNodeEnd(ifFalse) - beg;
            ConditionalExpression ce = new ConditionalExpression(beg, len);
            ce.setLineColumnNumber(pn.getLineno(), pn.getColumn());
            ce.setTestExpression(pn);
            ce.setTrueExpression(ifTrue);
            ce.setFalseExpression(ifFalse);
            ce.setQuestionMarkPosition(qmarkPos - beg);
            ce.setColonPosition(colonPos - beg);
            pn = ce;
        }
        return pn;
    }

    private AstNode nullishCoalescingExpr() throws IOException {
        AstNode pn = orExpr();
        if (matchToken(Token.NULLISH_COALESCING, true)) {
            int opPos = ts.tokenBeg;
            AstNode rn = nullishCoalescingExpr();

            // Cannot immediately contain, or be contained within, an && or || operation.
            if (pn.getType() == Token.OR
                    || pn.getType() == Token.AND
                    || rn.getType() == Token.OR
                    || rn.getType() == Token.AND) {
                reportError("msg.nullish.bad.token");
            }

            pn = new InfixExpression(Token.NULLISH_COALESCING, pn, rn, opPos);
        }
        return pn;
    }

    private AstNode orExpr() throws IOException {
        AstNode pn = andExpr();
        if (matchToken(Token.OR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.OR, pn, orExpr(), opPos);
        }
        return pn;
    }

    private AstNode andExpr() throws IOException {
        AstNode pn = bitOrExpr();
        if (matchToken(Token.AND, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.AND, pn, andExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitOrExpr() throws IOException {
        AstNode pn = bitXorExpr();
        while (matchToken(Token.BITOR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITOR, pn, bitXorExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitXorExpr() throws IOException {
        AstNode pn = bitAndExpr();
        while (matchToken(Token.BITXOR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITXOR, pn, bitAndExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitAndExpr() throws IOException {
        AstNode pn = eqExpr();
        while (matchToken(Token.BITAND, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITAND, pn, eqExpr(), opPos);
        }
        return pn;
    }

    private AstNode eqExpr() throws IOException {
        AstNode pn = relExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.EQ:
                case Token.NE:
                case Token.SHEQ:
                case Token.SHNE:
                    consumeToken();
                    int parseToken = tt;
                    pn = new InfixExpression(parseToken, pn, relExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode relExpr() throws IOException {
        AstNode pn;
        if (peekToken() == Token.PRIVATE_NAME) {
            // #x in obj
            consumeToken();
            Name name = privateReference();
            int opPos = ts.tokenBeg;
            if (peekToken() != Token.IN || inForInit) {
                reportError("msg.private.unexpected", name.getIdentifier());
            }
            consumeToken();
            pn = new InfixExpression(Token.IN, name, shiftExpr(), opPos);
        } else {
            pn = shiftExpr();
        }
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.IN:
                    if (inForInit) break;
                // fall through
                case Token.INSTANCEOF:
                case Token.LE:
                case Token.LT:
                case Token.GE:
                case Token.GT:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, shiftExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode shiftExpr() throws IOException {
        AstNode pn = addExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.LSH:
                case Token.URSH:
                case Token.RSH:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, addExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode addExpr() throws IOException {
        AstNode pn = mulExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            if (tt == Token.ADD || tt == Token.SUB) {
                consumeToken();
                pn = new InfixExpression(tt, pn, mulExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode mulExpr() throws IOException {
        AstNode pn = expExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.MUL:
                case Token.DIV:
                case Token.MOD:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, expExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode expExpr() throws IOException {
        AstNode pn = unaryExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.EXP:
                    if (pn instanceof UnaryExpression) {
                        reportError(
                                "msg.no.unary.expr.on.left.exp",
                                AstNode.operatorToString(pn.getType()));
                        return makeErrorNode();
                    }
                    consumeToken();
                    pn = new InfixExpression(tt, pn, expExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    /** AwaitExpression (ECMA-262 15.8): a suspension of the generator an async body runs as. */
    private AstNode awaitExpr() throws IOException {
        consumeToken();
        checkEscapedKeyword();
        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg;
        if (isModuleTopLevel()) {
            moduleInfo.hasTopLevelAwait = true;
        } else if (!inAsyncFunction() || functionOfParams == currentScriptOrFn) {
            // in a module outside an async function, or in the parameters of an async function
            reportError("msg.bad.await");
        }
        AstNode operand = unaryExpr();
        Yield node = new Yield(pos, getNodeEnd(operand) - pos, operand, false);
        node.setIsAwait();
        if (inAsyncGenerator()) {
            node.setIsAwaitRequest();
        }
        node.setLineColumnNumber(lineno, column);
        return node;
    }

    private AstNode unaryExpr() throws IOException {
        AstNode node;
        int tt = peekToken();
        if (tt == Token.COMMENT) {
            consumeToken();
            tt = peekUntilNonComment(tt);
        }
        int line, column;

        switch (tt) {
            case Token.AWAIT:
                return awaitExpr();

            case Token.VOID:
            case Token.NOT:
            case Token.BITNOT:
            case Token.TYPEOF:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                node = new UnaryExpression(tt, ts.tokenBeg, unaryExpr());
                node.setLineColumnNumber(line, column);
                return node;

            case Token.ADD:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                // Convert to special POS token in parse tree
                node = new UnaryExpression(Token.POS, ts.tokenBeg, unaryExpr());
                node.setLineColumnNumber(line, column);
                return node;

            case Token.SUB:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                // Convert to special NEG token in parse tree
                node = new UnaryExpression(Token.NEG, ts.tokenBeg, unaryExpr());
                node.setLineColumnNumber(line, column);
                return node;

            case Token.INC:
            case Token.DEC:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                UpdateExpression expr = new UpdateExpression(tt, ts.tokenBeg, memberExpr(true));
                expr.setLineColumnNumber(line, column);
                checkBadIncDec(expr);
                return expr;

            case Token.DELPROP:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                node = new UnaryExpression(tt, ts.tokenBeg, unaryExpr());
                if (inUseStrictDirective
                        && compilerEnv.isLatestEcmaScript()
                        && removeParens(((UnaryExpression) node).getOperand()) instanceof Name) {
                    reportError("msg.delete.name.strict");
                }
                AstNode deleted = removeParens(((UnaryExpression) node).getOperand());
                if (deleted instanceof ElementGet
                        && isPrivateName(((ElementGet) deleted).getElement())) {
                    reportError("msg.private.delete");
                }
                node.setLineColumnNumber(line, column);
                return node;

            case Token.ERROR:
                consumeToken();
                return makeErrorNode();
            case Token.LT:
                // XML stream encountered in expression.
                if (compilerEnv.isXmlAvailable()) {
                    consumeToken();
                    return memberExprTail(true, xmlInitializer());
                }
            // Fall thru to the default handling of RELOP
            // fall through

            default:
                AstNode pn = memberExpr(true);
                // Don't look across a newline boundary for a postfix incop.
                tt = peekTokenOrEOL();
                if (!(tt == Token.INC || tt == Token.DEC)) {
                    return pn;
                }
                consumeToken();
                UpdateExpression uexpr = new UpdateExpression(tt, ts.tokenBeg, pn, true);
                uexpr.setLineColumnNumber(pn.getLineno(), pn.getColumn());
                checkBadIncDec(uexpr);
                return uexpr;
        }
    }

    private AstNode xmlInitializer() throws IOException {
        if (currentToken != Token.LT) codeBug();
        int pos = ts.tokenBeg, tt = ts.getFirstXMLToken();
        if (tt != Token.XML && tt != Token.XMLEND) {
            reportError("msg.syntax");
            return makeErrorNode();
        }

        XmlLiteral pn = new XmlLiteral(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());

        for (; ; tt = ts.getNextXMLToken()) {
            switch (tt) {
                case Token.XML:
                    pn.addFragment(new XmlString(ts.tokenBeg, ts.getString()));
                    mustMatchToken(Token.LC, "msg.syntax", true);
                    int beg = ts.tokenBeg;
                    AstNode expr =
                            (peekToken() == Token.RC)
                                    ? new EmptyExpression(beg, ts.tokenEnd - beg)
                                    : expr(false);
                    mustMatchToken(Token.RC, "msg.syntax", true);
                    XmlExpression xexpr = new XmlExpression(beg, expr);
                    xexpr.setIsXmlAttribute(ts.isXMLAttribute());
                    xexpr.setLength(ts.tokenEnd - beg);
                    pn.addFragment(xexpr);
                    break;

                case Token.XMLEND:
                    pn.addFragment(new XmlString(ts.tokenBeg, ts.getString()));
                    return pn;

                default:
                    reportError("msg.syntax");
                    return makeErrorNode();
            }
        }
    }

    /** Set by argumentList when the list ends with a comma. */
    private boolean argumentListTrailingComma;

    private List<AstNode> argumentList() throws IOException {
        argumentListTrailingComma = false;
        if (matchToken(Token.RP, true)) return null;

        List<AstNode> result = new ArrayList<>();
        boolean wasInForInit = inForInit;
        inForInit = false;
        boolean wasInCover = inParenthesizedCover;
        inParenthesizedCover = false;
        try {
            do {
                if (peekToken() == Token.RP) {
                    // Quick fix to handle scenario like f1(a,); but not f1(a,b
                    argumentListTrailingComma = true;
                    break;
                }
                if (peekToken() == Token.YIELD && !compilerEnv.isLatestEcmaScript()) {
                    reportError("msg.yield.parenthesized");
                }
                AstNode en = assignExpr();
                if (en instanceof Spread && !compilerEnv.isLatestEcmaScript()) {
                    // spread arguments are a feature of VERSION_ECMASCRIPT
                    reportError("msg.syntax");
                }
                if (peekToken() == Token.FOR) {
                    try {
                        result.add(generatorExpression(en, 0, true));
                    } catch (IOException ex) {
                        // #TODO
                    }
                } else {
                    result.add(en);
                }
            } while (matchToken(Token.COMMA, true));
        } finally {
            inForInit = wasInForInit;
            inParenthesizedCover = wasInCover;
        }

        mustMatchToken(Token.RP, "msg.no.paren.arg", true);
        return result;
    }

    /**
     * Parse a new-expression, or if next token isn't {@link Token#NEW}, a primary expression.
     *
     * @param allowCallSyntax passed down to {@link #memberExprTail}
     */
    private AstNode memberExpr(boolean allowCallSyntax) throws IOException {
        int tt = peekToken();
        AstNode pn;

        if (tt != Token.NEW) {
            pn = primaryExpr();
        } else {
            consumeToken();
            checkEscapedKeyword();
            int pos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();

            // Detect new.target meta-property
            if (peekToken() == Token.DOT) {
                consumeToken(); // consume '.'

                if ((compilerEnv.isAllowNewTarget()
                                || (compilerEnv.isLatestEcmaScript()
                                        ? insideNonArrowFunction()
                                        : nestingOfFunction > 0))
                        && matchToken(Token.NAME, true)
                        && "target".equals(ts.getString())) {
                    checkEscapedKeyword();
                    int end = ts.tokenEnd;
                    KeywordLiteral nt = new KeywordLiteral(pos, end - pos, Token.NEW_TARGET);
                    nt.setLineColumnNumber(lineno, column);
                    return memberExprTail(allowCallSyntax, nt);
                }
                reportError("msg.bad.new.dot");
                return makeErrorNode();
            }
            if (peekToken() == Token.RESERVED
                    && "import".equals(ts.getString())
                    && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                // new import(...): an ImportCall is not a MemberExpression
                TokenStream.LookAhead next = ts.lookAhead();
                if (next != null && next.next == '(') {
                    reportError("msg.syntax");
                }
            }

            NewExpression nx = new NewExpression(pos);

            AstNode target = memberExpr(false);
            int end = getNodeEnd(target);
            nx.setTarget(target);
            nx.setLineColumnNumber(lineno, column);

            int lp = -1;
            if (matchToken(Token.LP, true)) {
                lp = ts.tokenBeg;
                List<AstNode> args = argumentList();
                if (args != null && args.size() > ARGC_LIMIT)
                    reportError("msg.too.many.constructor.args");
                int rp = ts.tokenBeg;
                end = ts.tokenEnd;
                if (args != null) nx.setArguments(args);
                nx.setParens(lp - pos, rp - pos);
            }

            // Experimental syntax: allow an object literal to follow a new
            // expression, which will mean a kind of anonymous class built with
            // the JavaAdapter.  the object literal will be passed as an
            // additional argument to the constructor.
            if (!inClassHeritage && matchToken(Token.LC, true)) {
                ObjectLiteral initializer = objectLiteral();
                end = getNodeEnd(initializer);
                nx.setInitializer(initializer);
            }
            nx.setLength(end - pos);
            pn = nx;
        }
        return memberExprTail(allowCallSyntax, pn);
    }

    /**
     * Parse any number of "(expr)", "[expr]" ".expr", "?.expr", "..expr", ".(expr)" or "?.(expr)"
     * constructs trailing the passed expression.
     *
     * @param pn the non-null parent node
     * @return the outermost (lexically last occurring) expression, which will have the passed
     *     parent node as a descendant
     */
    private AstNode memberExprTail(boolean allowCallSyntax, AstNode pn) throws IOException {
        // we no longer return null for errors, so this won't be null
        if (pn == null) codeBug();
        int pos = pn.getPosition();
        int lineno, column;
        boolean isOptionalChain = false;
        tailLoop:
        for (; ; ) {
            lineno = lineNumber();
            column = columnNumber();
            int tt = peekToken();
            switch (tt) {
                case Token.DOT:
                case Token.QUESTION_DOT:
                case Token.DOTDOT:
                    isOptionalChain |= (tt == Token.QUESTION_DOT);
                    pn = propertyAccess(tt, pn, isOptionalChain);
                    break;

                case Token.DOTQUERY:
                    consumeToken();
                    int opPos = ts.tokenBeg, rp = -1;
                    mustHaveXML();
                    setRequiresActivation();
                    AstNode filter = expr(false);
                    int end = getNodeEnd(filter);
                    if (mustMatchToken(Token.RP, "msg.no.paren", true)) {
                        rp = ts.tokenBeg;
                        end = ts.tokenEnd;
                    }
                    XmlDotQuery q = new XmlDotQuery(pos, end - pos);
                    q.setLeft(pn);
                    q.setRight(filter);
                    q.setOperatorPosition(opPos);
                    q.setRp(rp - pos);
                    q.setLineColumnNumber(lineno, column);
                    pn = q;
                    break;

                case Token.LB:
                    consumeToken();
                    pn = makeElemGet(pn, ts.tokenBeg);
                    if (isOptionalChain && compilerEnv.isLatestEcmaScript()) {
                        // a?.b[c]: the element is not evaluated when a is null or undefined
                        pn.setType(Token.QUESTION_DOT);
                    }
                    break;

                case Token.LP:
                    if (!allowCallSyntax) {
                        break tailLoop;
                    }
                    pn = makeFunctionCall(pn, pos, isOptionalChain);
                    break;
                case Token.COMMENT:
                    // Ignoring all the comments, because previous statement may not be terminated
                    // properly.
                    int currentFlagTOken = currentFlaggedToken;
                    peekUntilNonComment(tt);
                    currentFlaggedToken =
                            (currentFlaggedToken & TI_AFTER_EOL) != 0
                                    ? currentFlaggedToken
                                    : currentFlagTOken;
                    break;
                case Token.TEMPLATE_LITERAL:
                    consumeToken();
                    if (isOptionalChain && compilerEnv.isLatestEcmaScript()) {
                        reportError("msg.optional.chain.template");
                    }
                    pn = taggedTemplateLiteral(pn);
                    break;
                default:
                    break tailLoop;
            }
        }
        return pn;
    }

    private FunctionCall makeFunctionCall(AstNode pn, int pos, boolean isOptionalChain)
            throws IOException {
        consumeToken();
        checkCallRequiresActivation(pn);
        FunctionCall f = new FunctionCall(pos);
        f.setTarget(pn);
        if (functionOfParams != null && functionOfParams == currentScriptOrFn) {
            f.markInParameters();
        }
        f.setLp(ts.tokenBeg - pos);
        List<AstNode> args = argumentList();
        if (args != null && args.size() > ARGC_LIMIT) reportError("msg.too.many.function.args");
        f.setArguments(args);
        if (argumentListTrailingComma && pn == asyncCallTarget) {
            f.putIntProp(Node.TRAILING_COMMA, 1);
        }
        f.setRp(ts.tokenBeg - pos);
        f.setLength(ts.tokenEnd - pos);
        if (isOptionalChain) {
            f.markIsOptionalCall();
        }
        return f;
    }

    private AstNode taggedTemplateLiteral(AstNode pn) throws IOException {
        AstNode templateLiteral = templateLiteral(true);
        TaggedTemplateLiteral tagged = new TaggedTemplateLiteral();
        tagged.setTarget(pn);
        tagged.setTemplateLiteral(templateLiteral);
        tagged.setLineColumnNumber(pn.getLineno(), pn.getColumn());
        return tagged;
    }

    /**
     * Handles any construct following a "." or ".." operator.
     *
     * @param pn the left-hand side (target) of the operator. Never null.
     * @param isOptionalChain whether we are inside an optional chain, i.e. whether a preceding
     *     property access was done via the {@code ?.} operator
     * @return a PropertyGet, XmlMemberGet, or ErrorNode
     */
    private AstNode propertyAccess(int tt, AstNode pn, boolean isOptionalChain) throws IOException {
        if (pn == null) codeBug();
        if (pn.getType() == Token.SUPER && isOptionalChain) {
            reportError("msg.optional.super");
            return makeErrorNode();
        }

        int memberTypeFlags = 0,
                lineno = lineNumber(),
                dotPos = ts.tokenBeg,
                column = columnNumber();
        consumeToken();

        if (tt == Token.DOTDOT) {
            mustHaveXML();
            memberTypeFlags = Node.DESCENDANTS_FLAG;
        }

        AstNode ref = null; // right side of . or .. operator
        int token = nextToken();
        switch (token) {
            case Token.THROW:
                // needed for generator.throw();
                saveNameTokenData(ts.tokenBeg, "throw", lineNumber(), columnNumber());
                ref = propertyName(-1, memberTypeFlags);
                break;

            case Token.NAME:
                // handles: name, ns::name, ns::*, ns::[expr]
                ref = propertyName(-1, memberTypeFlags);
                break;

            case Token.MUL:
                if (compilerEnv.isXmlAvailable()) {
                    // handles: *, *::name, *::*, *::[expr]
                    saveNameTokenData(ts.tokenBeg, "*", lineNumber(), columnNumber());
                    ref = propertyName(-1, memberTypeFlags);
                    break;
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            case Token.XMLATTR:
                if (compilerEnv.isXmlAvailable()) {
                    // handles: '@attr', '@ns::attr', '@ns::*', '@ns::*',
                    //          '@::attr', '@::*', '@*', '@*::attr', '@*::*'
                    ref = attributeAccess();
                    break;
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            case Token.RESERVED:
                {
                    String name = ts.getString();
                    saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                    ref = propertyName(-1, memberTypeFlags);
                    break;
                }

            case Token.PRIVATE_NAME:
                {
                    if (pn.getType() == Token.SUPER || tt == Token.DOTDOT) {
                        reportError("msg.private.unexpected", ts.getString());
                        return makeErrorNode();
                    }
                    // obj.#x: an element access whose key is the binding of the private name
                    Name name = privateReference();
                    int pos = pn.getPosition();
                    ElementGet g = new ElementGet(pos, ts.tokenEnd - pos);
                    g.setTarget(pn);
                    g.setElement(name);
                    g.setLineColumnNumber(lineno, column);
                    if (isOptionalChain) {
                        g.setType(Token.QUESTION_DOT);
                    }
                    return g;
                }

            case Token.LB:
                if (tt == Token.QUESTION_DOT) {
                    // a ?.[ expr ]
                    consumeToken();
                    ElementGet g = makeElemGet(pn, ts.tokenBeg);
                    g.setType(Token.QUESTION_DOT);
                    return g;
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            case Token.LP:
                if (tt == Token.QUESTION_DOT) {
                    // a function call such as f?.()
                    return makeFunctionCall(pn, pn.getPosition(), isOptionalChain);
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            default:
                if (compilerEnv.isReservedKeywordAsIdentifier()) {
                    // allow keywords as property names, e.g. ({if: 1})
                    String name = Token.keywordToName(token);
                    if (name != null) {
                        saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                        ref = propertyName(-1, memberTypeFlags);
                        break;
                    }
                }
                reportError("msg.no.name.after.dot");
                return makeErrorNode();
        }

        boolean xml = ref instanceof XmlRef;
        InfixExpression result = xml ? new XmlMemberGet() : new PropertyGet();
        if (xml && tt == Token.DOT) result.setType(Token.DOT);
        if (isOptionalChain) {
            result.setType(Token.QUESTION_DOT);
        }
        int pos = pn.getPosition();
        result.setPosition(pos);
        result.setLength(getNodeEnd(ref) - pos);
        result.setOperatorPosition(dotPos - pos);
        result.setLineColumnNumber(lineno, column);
        result.setLeft(pn); // do this after setting position
        result.setRight(ref);
        return result;
    }

    private ElementGet makeElemGet(AstNode pn, int lb) throws IOException {
        int pos = pn.getPosition();
        AstNode expr = expr(false);
        int end = getNodeEnd(expr);
        int rb = -1;
        if (mustMatchToken(Token.RB, "msg.no.bracket.index", true)) {
            rb = ts.tokenBeg;
            end = ts.tokenEnd;
        }
        ElementGet g = new ElementGet(pos, end - pos);
        g.setTarget(pn);
        g.setElement(expr);
        g.setParens(lb, rb);
        return g;
    }

    /**
     * Xml attribute expression:
     *
     * <p>{@code @attr}, {@code @ns::attr}, {@code @ns::*}, {@code @ns::*}, {@code @*},
     * {@code @*::attr}, {@code @*::*}, {@code @ns::[expr]}, {@code @*::[expr]}, {@code @[expr]}
     *
     * <p>Called if we peeked an '@' token.
     */
    private AstNode attributeAccess() throws IOException {
        int tt = nextToken(), atPos = ts.tokenBeg;

        switch (tt) {
            // handles: @name, @ns::name, @ns::*, @ns::[expr]
            case Token.NAME:
                return propertyName(atPos, 0);
            case Token.RESERVED:
                String name = ts.getString();
                saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                return propertyName(atPos, 0);
            // handles: @*, @*::name, @*::*, @*::[expr]
            case Token.MUL:
                saveNameTokenData(ts.tokenBeg, "*", lineNumber(), columnNumber());
                return propertyName(atPos, 0);

            // handles @[expr]
            case Token.LB:
                return xmlElemRef(atPos, null, -1);

            default:
                {
                    if (compilerEnv.isReservedKeywordAsIdentifier()) {
                        // allow keywords as property names, e.g. ({if: 1})
                        name = Token.keywordToName(tt);
                        if (name != null) {
                            saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                            return propertyName(atPos, 0);
                        }
                    }
                }
                reportError("msg.no.name.after.xmlAttr");
                return makeErrorNode();
        }
    }

    /**
     * Check if :: follows name in which case it becomes a qualified name.
     *
     * @param atPos a natural number if we just read an '@' token, else -1
     * @param memberTypeFlags flags tracking whether we're a '.' or '..' child
     * @return an XmlRef node if it's an attribute access, a child of a '..' operator, or the name
     *     is followed by ::. For a plain name, returns a Name node. Returns an ErrorNode for
     *     malformed XML expressions. (For now - might change to return a partial XmlRef.)
     */
    private AstNode propertyName(int atPos, int memberTypeFlags) throws IOException {
        int pos = atPos != -1 ? atPos : ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        int colonPos = -1;
        Name name = createNameNode(true, currentToken);
        Name ns = null;

        if (matchToken(Token.COLONCOLON, true)) {
            ns = name;
            colonPos = ts.tokenBeg;

            int nt = nextToken();
            switch (nt) {
                // handles name::name
                case Token.NAME:
                    name = createNameNode();
                    break;
                case Token.RESERVED:
                    {
                        String realName = ts.getString();
                        saveNameTokenData(ts.tokenBeg, realName, lineNumber(), columnNumber());
                        name = createNameNode(false, -1);
                        break;
                    }
                case Token.MUL:
                    saveNameTokenData(ts.tokenBeg, "*", lineNumber(), columnNumber());
                    name = createNameNode(false, -1);
                    break;

                // handles name::[expr] or *::[expr]
                case Token.LB:
                    return xmlElemRef(atPos, ns, colonPos);

                default:
                    {
                        if (compilerEnv.isReservedKeywordAsIdentifier()) {
                            // allow keywords as property names, e.g. ({if: 1})
                            String realName = Token.keywordToName(nt);
                            if (name != null) {
                                saveNameTokenData(
                                        ts.tokenBeg, realName, lineNumber(), columnNumber());
                                name = createNameNode(false, -1);
                                break;
                            }
                        }
                    }
                    reportError("msg.no.name.after.coloncolon");
                    return makeErrorNode();
            }
        }

        if (ns == null && memberTypeFlags == 0 && atPos == -1) {
            return name;
        }

        XmlPropRef ref = new XmlPropRef(pos, getNodeEnd(name) - pos);
        ref.setAtPos(atPos);
        ref.setNamespace(ns);
        ref.setColonPos(colonPos);
        ref.setPropName(name);
        ref.setLineColumnNumber(lineno, column);
        return ref;
    }

    /**
     * Parse the [expr] portion of an xml element reference, e.g. @[expr], @*::[expr], or
     * ns::[expr].
     */
    private XmlElemRef xmlElemRef(int atPos, Name namespace, int colonPos) throws IOException {
        int lb = ts.tokenBeg, rb = -1, pos = atPos != -1 ? atPos : lb;
        AstNode expr = expr(false);
        int end = getNodeEnd(expr);
        if (mustMatchToken(Token.RB, "msg.no.bracket.index", true)) {
            rb = ts.tokenBeg;
            end = ts.tokenEnd;
        }
        XmlElemRef ref = new XmlElemRef(pos, end - pos);
        ref.setNamespace(namespace);
        ref.setColonPos(colonPos);
        ref.setAtPos(atPos);
        ref.setExpression(expr);
        ref.setBrackets(lb, rb);
        return ref;
    }

    private AstNode destructuringAssignExpr() throws IOException, ParserException {
        try {
            inDestructuringAssignment = true;
            return assignExpr();
        } finally {
            inDestructuringAssignment = false;
        }
    }

    private AstNode destructuringPrimaryExpr() throws IOException, ParserException {
        try {
            inDestructuringAssignment = true;
            return primaryExpr();
        } finally {
            inDestructuringAssignment = false;
        }
    }

    private AstNode primaryExpr() throws IOException {
        int ttFlagged = peekFlaggedToken();
        int tt = ttFlagged & CLEAR_TI_MASK;

        switch (tt) {
            case Token.FUNCTION:
                consumeToken();
                return function(FunctionNode.FUNCTION_EXPRESSION);

            case Token.LB:
                consumeToken();
                return checkCoverLiteral(arrayLiteral());

            case Token.LC:
                consumeToken();
                return checkCoverLiteral(objectLiteral());

            case Token.LET:
                if (compilerEnv.isLatestEcmaScript() && !inUseStrictDirective) {
                    // let is an identifier in sloppy code
                    letAsName();
                    consumeToken();
                    return createNameNode(true, Token.NAME);
                }
                consumeToken();
                return let(false, ts.tokenBeg);

            case Token.LP:
                consumeToken();
                return parenExpr();

            case Token.XMLATTR:
                consumeToken();
                mustHaveXML();
                return attributeAccess();

            case Token.NAME:
                if (isAsyncFunctionStart()) {
                    return asyncFunction(FunctionNode.FUNCTION_EXPRESSION);
                }
                if (isAsyncArrowWithIdentifierStart()) {
                    return asyncArrowWithIdentifier();
                }
                boolean asyncName =
                        "async".equals(ts.getString())
                                && !ts.nameHadEscape
                                && compilerEnv.isLatestEcmaScript();
                consumeToken();
                AstNode nameNode = name(ttFlagged, tt);
                if (inFieldInitializer
                        && nameNode instanceof Name
                        && "arguments".equals(((Name) nameNode).getIdentifier())) {
                    // ContainsArguments of a field initializer (15.7.1)
                    reportError("msg.class.field.arguments");
                }
                if (asyncName && peekFlaggedToken() == Token.LP) {
                    // async (...): a call, or the parameters of an async arrow function
                    asyncCallTarget = nameNode;
                }
                return nameNode;

            case Token.NUMBER:
            case Token.BIGINT:
                {
                    consumeToken();
                    return createNumericLiteral(tt, false);
                }

            case Token.STRING:
                consumeToken();
                return createStringLiteral();

            case Token.DIV:
            case Token.ASSIGN_DIV:
                consumeToken();
                // Got / or /= which in this context means a regexp
                ts.readRegExp(tt);
                int pos = ts.tokenBeg, end = ts.tokenEnd;
                RegExpLiteral re = new RegExpLiteral(pos, end - pos);
                re.setValue(ts.getString());
                re.setFlags(ts.readAndClearRegExpFlags());
                re.setLineColumnNumber(lineNumber(), columnNumber());
                return re;

            case Token.UNDEFINED:
                {
                    consumeToken();
                    pos = ts.tokenBeg;
                    end = ts.tokenEnd;
                    if (hasUndefinedBeenRedefined) {
                        return new Name(pos, end - pos, "undefined");
                    }

                    KeywordLiteral keywordLiteral = new KeywordLiteral(pos, end - pos, tt);
                    keywordLiteral.setLineColumnNumber(lineNumber(), columnNumber());
                    return keywordLiteral;
                }

            case Token.NULL:
            case Token.THIS:
            case Token.FALSE:
            case Token.TRUE:
                {
                    consumeToken();
                    checkEscapedKeyword();
                    pos = ts.tokenBeg;
                    end = ts.tokenEnd;
                    KeywordLiteral keywordLiteral = new KeywordLiteral(pos, end - pos, tt);
                    keywordLiteral.setLineColumnNumber(lineNumber(), columnNumber());
                    return keywordLiteral;
                }

            case Token.SUPER:
                if (((insideFunctionParams() || insideFunctionBody()) && insideMethod)
                        || compilerEnv.isAllowSuper()) {
                    consumeToken();
                    pos = ts.tokenBeg;
                    end = ts.tokenEnd;
                    KeywordLiteral keywordLiteral = new KeywordLiteral(pos, end - pos, tt);
                    keywordLiteral.setLineColumnNumber(lineNumber(), columnNumber());
                    return keywordLiteral;
                } else {
                    reportError("msg.super.shorthand.function");
                }
                break;

            case Token.TEMPLATE_LITERAL:
                consumeToken();
                return templateLiteral(false);

            case Token.DOTDOTDOT:
                // spread operator for arrow function parameters
                consumeToken();
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    int spreadPos = ts.tokenBeg;
                    int spreadLineno = lineNumber();
                    int spreadColumn = columnNumber();
                    AstNode exprNode = assignExpr();
                    if (exprNode instanceof FunctionNode
                            && ((FunctionNode) exprNode).getFunctionType()
                                    == FunctionNode.ARROW_FUNCTION) {
                        // ...x => x is invalid
                        reportError("msg.no.parm", spreadPos, ts.tokenEnd - spreadPos);
                        return makeErrorNode();
                    }
                    Spread spread = new Spread(spreadPos, ts.tokenEnd - spreadPos);
                    spread.setLineColumnNumber(spreadLineno, spreadColumn);
                    spread.setExpression(exprNode);
                    return spread;
                } else {
                    reportError("msg.syntax");
                }
                break;

            case Token.RESERVED:
                if (isClassKeyword()) {
                    return classDefinition(false);
                }
                if ("import".equals(ts.getString()) && compilerEnv.isLatestEcmaScript()) {
                    return importExpression();
                }
                consumeToken();
                reportError("msg.reserved.id", ts.getString());
                break;

            case Token.ERROR:
                consumeToken();
                // the scanner or one of its subroutines reported the error.
                break;

            case Token.EOF:
                consumeToken();
                reportError("msg.unexpected.eof");
                break;

            default:
                consumeToken();
                reportError("msg.syntax");
                break;
        }
        // should only be reachable in IDE/error-recovery mode
        consumeToken();
        return makeErrorNode();
    }

    private AstNode parenExpr() throws IOException {
        boolean wasInForInit = inForInit;
        inForInit = false;
        try {
            Comment jsdocNode = getAndResetJsDoc();
            int lineno = lineNumber(), column = columnNumber();
            int begin = ts.tokenBeg;
            boolean wasInCover = inParenthesizedCover;
            int pendingBefore = pendingCoverInitializedNames;
            AstNode e;
            inParenthesizedCover = true;
            try {
                e = (peekToken() == Token.RP ? new EmptyExpression(begin) : expr(true));
            } finally {
                inParenthesizedCover = wasInCover;
            }
            boolean hasCoverInitializedName = pendingCoverInitializedNames != pendingBefore;
            pendingCoverInitializedNames = pendingBefore;
            if (peekToken() == Token.FOR) {
                return generatorExpression(e, begin);
            }
            mustMatchToken(Token.RP, "msg.no.paren", true);

            int length = ts.tokenEnd - begin;

            boolean hasObjectLiteralDestructuring =
                    e.getIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 0) == 1
                            || hasCoverInitializedName;
            boolean hasTrailingComma = e.getIntProp(Node.TRAILING_COMMA, 0) == 1;
            if ((hasTrailingComma || hasObjectLiteralDestructuring || e.getType() == Token.EMPTY)
                    && peekToken() != Token.ARROW) {
                reportError("msg.syntax");
                return makeErrorNode();
            }

            ParenthesizedExpression pn = new ParenthesizedExpression(begin, length, e);
            pn.setLineColumnNumber(lineno, column);
            if (jsdocNode == null) {
                jsdocNode = getAndResetJsDoc();
            }
            if (jsdocNode != null) {
                pn.setJsDocNode(jsdocNode);
            }
            if (hasTrailingComma) {
                pn.putIntProp(Node.TRAILING_COMMA, 1);
            }
            return pn;
        } finally {
            inForInit = wasInForInit;
        }
    }

    private AstNode name(int ttFlagged, int tt) throws IOException {
        String nameString = ts.getString();
        int namePos = ts.tokenBeg, nameLineno = lineNumber(), nameColumn = columnNumber();
        if (0 != (ttFlagged & TI_CHECK_LABEL) && peekToken() == Token.COLON) {
            // Do not consume colon.  It is used as an unwind indicator
            // to return to statementHelper.
            Label label = new Label(namePos, ts.tokenEnd - namePos);
            label.setName(nameString);
            label.setLineColumnNumber(lineNumber(), columnNumber());
            return label;
        }
        // Not a label.  Unfortunately peeking the next token to check for
        // a colon has biffed ts.tokenBeg, ts.tokenEnd.  We store the name's
        // bounds in instance vars and createNameNode uses them.
        saveNameTokenData(namePos, nameString, nameLineno, nameColumn);

        if (compilerEnv.isXmlAvailable()) {
            return propertyName(-1, 0);
        }
        return createNameNode(true, Token.NAME);
    }

    /** May return an {@link ArrayLiteral} or {@link ArrayComprehension}. */
    private AstNode arrayLiteral() throws IOException {
        // the in operator is allowed inside brackets, also in a for head
        boolean wasInForInit = inForInit;
        if (compilerEnv.isLatestEcmaScript()) {
            inForInit = false;
        }
        // [{ x = 1 }] = a: a CoverInitializedName nested in a literal is valid if the literal
        // becomes a pattern
        boolean wasInCover = inParenthesizedCover;
        int pendingBefore = pendingCoverInitializedNames;
        inParenthesizedCover = true;
        try {
            AstNode literal = arrayLiteralImpl();
            if (pendingCoverInitializedNames != pendingBefore) {
                literal.putIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 1);
            }
            return literal;
        } finally {
            inForInit = wasInForInit;
            inParenthesizedCover = wasInCover;
            pendingCoverInitializedNames = pendingBefore;
        }
    }

    private AstNode arrayLiteralImpl() throws IOException {
        if (currentToken != Token.LB) codeBug();
        int pos = ts.tokenBeg, end = ts.tokenEnd, lineno = lineNumber(), column = columnNumber();
        List<AstNode> elements = new ArrayList<>();
        ArrayLiteral pn = new ArrayLiteral(pos);
        boolean after_lb_or_comma = true;
        int afterComma = -1;
        int skipCount = 0;
        for (; ; ) {
            int tt = peekToken();
            if (tt == Token.COMMA) {
                consumeToken();
                afterComma = ts.tokenEnd;
                if (!after_lb_or_comma) {
                    after_lb_or_comma = true;
                } else {
                    elements.add(new EmptyExpression(ts.tokenBeg, 1));
                    skipCount++;
                }
            } else if (tt == Token.COMMENT) {
                consumeToken();
            } else if (tt == Token.RB) {
                consumeToken();
                // for ([a,] in obj) is legal, but for ([a] in obj) is
                // not since we have both key and value supplied. The
                // trick is that [a,] and [a] are equivalent in other
                // array literal contexts. So we calculate a special
                // length value just for destructuring assignment.
                end = ts.tokenEnd;
                pn.setDestructuringLength(elements.size() + (after_lb_or_comma ? 1 : 0));
                pn.setSkipCount(skipCount);
                if (afterComma != -1) warnTrailingComma(pos, elements, afterComma);
                break;
            } else if (tt == Token.FOR && !after_lb_or_comma && elements.size() == 1) {
                return arrayComprehension(elements.get(0), pos);
            } else if (tt == Token.EOF) {
                reportError("msg.no.bracket.arg");
                break;
            } else {
                if (!after_lb_or_comma) {
                    reportError("msg.no.bracket.arg");
                }
                AstNode element;
                if (tt == Token.DOTDOTDOT
                        && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    consumeToken();
                    int spreadPos = ts.tokenBeg;
                    int spreadLineno = lineNumber();
                    int spreadColumn = columnNumber();
                    AstNode exprNode = assignExpr();
                    element = new Spread(spreadPos, ts.tokenEnd - spreadPos);
                    element.setLineColumnNumber(spreadLineno, spreadColumn);
                    ((Spread) element).setExpression(exprNode);
                } else {
                    element = assignExpr();
                }
                elements.add(element);
                after_lb_or_comma = false;
                afterComma = -1;
            }
        }
        for (AstNode e : elements) {
            pn.addElement(e);
        }
        pn.setLength(end - pos);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    /**
     * Parse a JavaScript 1.7 Array comprehension.
     *
     * @param result the first expression after the opening left-bracket
     * @param pos start of LB token that begins the array comprehension
     * @return the array comprehension or an error node
     */
    private AstNode arrayComprehension(AstNode result, int pos) throws IOException {
        List<ArrayComprehensionLoop> loops = new ArrayList<>();
        while (peekToken() == Token.FOR) {
            loops.add(arrayComprehensionLoop());
        }
        int ifPos = -1;
        ConditionData data = null;
        if (peekToken() == Token.IF) {
            consumeToken();
            ifPos = ts.tokenBeg - pos;
            data = condition();
        }
        mustMatchToken(Token.RB, "msg.no.bracket.arg", true);
        ArrayComprehension pn = new ArrayComprehension(pos, ts.tokenEnd - pos);
        pn.setResult(result);
        pn.setLoops(loops);
        if (data != null) {
            pn.setIfPosition(ifPos);
            pn.setFilter(data.condition);
            pn.setFilterLp(data.lp - pos);
            pn.setFilterRp(data.rp - pos);
        }
        return pn;
    }

    private ArrayComprehensionLoop arrayComprehensionLoop() throws IOException {
        if (nextToken() != Token.FOR) codeBug();
        int pos = ts.tokenBeg;
        int eachPos = -1, lp = -1, rp = -1, inPos = -1;
        boolean isForOf = false;
        ArrayComprehensionLoop pn = new ArrayComprehensionLoop(pos);

        pushScope(pn);
        try {
            if (matchToken(Token.NAME, true)) {
                if ("each".equals(ts.getString())) {
                    eachPos = ts.tokenBeg - pos;
                } else {
                    reportError("msg.no.paren.for");
                }
            }
            if (mustMatchToken(Token.LP, "msg.no.paren.for", true)) {
                lp = ts.tokenBeg - pos;
            }

            AstNode iter = null;
            switch (peekToken()) {
                case Token.LB:
                case Token.LC:
                    // handle destructuring assignment
                    iter = destructuringPrimaryExpr();
                    markDestructuring(iter);
                    break;
                case Token.NAME:
                    consumeToken();
                    iter = createNameNode();
                    break;
                default:
                    reportError("msg.bad.var");
            }

            // Define as a let since we want the scope of the variable to
            // be restricted to the array comprehension
            if (iter.getType() == Token.NAME) {
                defineSymbol(Token.LET, ts.getString(), true);
            }

            switch (nextToken()) {
                case Token.IN:
                    inPos = ts.tokenBeg - pos;
                    break;
                case Token.NAME:
                    if ("of".equals(ts.getString())) {
                        if (eachPos != -1) {
                            reportError("msg.invalid.for.each");
                        }
                        inPos = ts.tokenBeg - pos;
                        isForOf = true;
                        break;
                    }
                // fall through
                default:
                    reportError("msg.in.after.for.name");
            }
            AstNode obj = expr(false);
            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl", true)) rp = ts.tokenBeg - pos;

            pn.setLength(ts.tokenEnd - pos);
            pn.setIterator(iter);
            pn.setIteratedObject(obj);
            pn.setInPosition(inPos);
            pn.setEachPosition(eachPos);
            pn.setIsForEach(eachPos != -1);
            pn.setParens(lp, rp);
            pn.setIsForOf(isForOf);
            return pn;
        } finally {
            popScope();
        }
    }

    private AstNode generatorExpression(AstNode result, int pos) throws IOException {
        return generatorExpression(result, pos, false);
    }

    private AstNode generatorExpression(AstNode result, int pos, boolean inFunctionParams)
            throws IOException {

        List<GeneratorExpressionLoop> loops = new ArrayList<>();
        while (peekToken() == Token.FOR) {
            loops.add(generatorExpressionLoop());
        }
        int ifPos = -1;
        ConditionData data = null;
        if (peekToken() == Token.IF) {
            consumeToken();
            ifPos = ts.tokenBeg - pos;
            data = condition();
        }
        if (!inFunctionParams) {
            mustMatchToken(Token.RP, "msg.no.paren.let", true);
        }
        GeneratorExpression pn = new GeneratorExpression(pos, ts.tokenEnd - pos);
        pn.setResult(result);
        pn.setLoops(loops);
        if (data != null) {
            pn.setIfPosition(ifPos);
            pn.setFilter(data.condition);
            pn.setFilterLp(data.lp - pos);
            pn.setFilterRp(data.rp - pos);
        }
        return pn;
    }

    private GeneratorExpressionLoop generatorExpressionLoop() throws IOException {
        if (nextToken() != Token.FOR) codeBug();
        int pos = ts.tokenBeg;
        int lp = -1, rp = -1, inPos = -1;
        GeneratorExpressionLoop pn = new GeneratorExpressionLoop(pos);

        pushScope(pn);
        try {
            if (mustMatchToken(Token.LP, "msg.no.paren.for", true)) {
                lp = ts.tokenBeg - pos;
            }

            AstNode iter = null;
            switch (peekToken()) {
                case Token.LB:
                case Token.LC:
                    // handle destructuring assignment
                    iter = destructuringPrimaryExpr();
                    markDestructuring(iter);
                    break;
                case Token.NAME:
                    consumeToken();
                    iter = createNameNode();
                    break;
                default:
                    reportError("msg.bad.var");
            }

            // Define as a let since we want the scope of the variable to
            // be restricted to the array comprehension
            if (iter.getType() == Token.NAME) {
                defineSymbol(Token.LET, ts.getString(), true);
            }

            if (mustMatchToken(Token.IN, "msg.in.after.for.name", true)) inPos = ts.tokenBeg - pos;
            AstNode obj = expr(false);
            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl", true)) rp = ts.tokenBeg - pos;

            pn.setLength(ts.tokenEnd - pos);
            pn.setIterator(iter);
            pn.setIteratedObject(obj);
            pn.setInPosition(inPos);
            pn.setParens(lp, rp);
            return pn;
        } finally {
            popScope();
        }
    }

    private static final int PROP_ENTRY = 1;
    private static final int GET_ENTRY = 2;
    private static final int SET_ENTRY = 4;
    private static final int METHOD_ENTRY = 8;

    private ObjectLiteral objectLiteral() throws IOException {
        boolean wasInForInit = inForInit;
        if (compilerEnv.isLatestEcmaScript()) {
            inForInit = false;
        }
        // [{ x = 1 }] = a: a CoverInitializedName nested in a literal is valid if the literal
        // becomes a pattern
        boolean wasInCover = inParenthesizedCover;
        int pendingBefore = pendingCoverInitializedNames;
        inParenthesizedCover = true;
        try {
            ObjectLiteral literal = objectLiteralImpl();
            if (pendingCoverInitializedNames != pendingBefore) {
                literal.putIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 1);
            }
            return literal;
        } finally {
            inForInit = wasInForInit;
            inParenthesizedCover = wasInCover;
            pendingCoverInitializedNames = pendingBefore;
        }
    }

    private ObjectLiteral objectLiteralImpl() throws IOException {
        int pos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        int afterComma = -1;
        List<AbstractObjectProperty> elems = new ArrayList<>();
        Set<String> getterNames = null;
        Set<String> setterNames = null;
        if (this.inUseStrictDirective) {
            getterNames = new HashSet<>();
            setterNames = new HashSet<>();
        }
        Comment objJsdocNode = getAndResetJsDoc();
        boolean objectLiteralDestructuringDefault = false;
        int protoDefinitions = 0;
        commaLoop:
        for (; ; ) {
            String propertyName = null;
            int entryKind = PROP_ENTRY;
            boolean isAsyncMethod = false;
            int tt = peekToken();
            Comment jsdocNode = getAndResetJsDoc();
            if (tt == Token.COMMENT) {
                consumeToken();
                tt = peekUntilNonComment(tt);
            }
            if (tt == Token.RC) {
                if (afterComma != -1) warnTrailingComma(pos, elems, afterComma);
                break commaLoop;
            }
            int propStart = ts.tokenBeg;
            AstNode pname = objliteralProperty();
            boolean pnameEscaped = ts != null && ts.nameHadEscape && pname instanceof Name;
            if (pname == null) {
                reportError("msg.bad.prop");
            } else if (pname instanceof Spread) {
                AstNode spreadExpr = ((Spread) pname).getExpression();
                if (spreadExpr instanceof Name || spreadExpr instanceof StringLiteral) {
                    // For complicated reasons, parsing a name does not advance the token
                    spreadExpr.setLineColumnNumber(lineNumber(), columnNumber());
                }

                SpreadObjectProperty spreadObjectProperty =
                        new SpreadObjectProperty((Spread) pname);
                elems.add(spreadObjectProperty);
            } else {
                propertyName = ts.getString();
                int ppos = ts.tokenBeg;
                consumeToken();
                if (pname instanceof Name || pname instanceof StringLiteral) {
                    // For complicated reasons, parsing a name does not advance the token
                    pname.setLineColumnNumber(lineNumber(), columnNumber());
                } else if (pname instanceof GeneratorMethodDefinition) {
                    // Same as above
                    ((GeneratorMethodDefinition) pname)
                            .getMethodName()
                            .setLineColumnNumber(lineNumber(), columnNumber());
                }

                // This code path needs to handle both destructuring object
                // literals like:
                // var {get, b} = {get: 1, b: 2};
                // and getters like:
                // var x = {get 1() { return 2; };
                // So we check a whitelist of tokens to check if we're at the
                // first case. (Because of keywords, the second case may be
                // many tokens.)
                int peeked = peekToken();
                if (peeked != Token.COMMA && peeked != Token.COLON && peeked != Token.RC) {
                    if (peeked == Token.ASSIGN) { // we have an object literal with
                        // destructuring assignment and a default value
                        objectLiteralDestructuringDefault = true;
                        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                            elems.add(plainProperty(pname, tt));
                            if (matchToken(Token.COMMA, true)) {
                                continue;
                            } else {
                                break commaLoop;
                            }
                        } else {
                            reportError("msg.default.args");
                        }
                    } else if (peeked == Token.LP) {
                        entryKind = METHOD_ENTRY;
                    } else if (pname.getType() == Token.NAME) {
                        if (pnameEscaped
                                && compilerEnv.isLatestEcmaScript()
                                && ("get".equals(propertyName) || "set".equals(propertyName))) {
                            // get and set written with escapes are not accessor keywords
                            reportError("msg.bad.prop");
                        }
                        if ("get".equals(propertyName)) {
                            entryKind = GET_ENTRY;
                        } else if ("set".equals(propertyName)) {
                            entryKind = SET_ENTRY;
                        } else if (isAsyncMethodStart(propertyName, pnameEscaped)) {
                            entryKind = METHOD_ENTRY;
                            isAsyncMethod = true;
                        }
                    }
                    if (entryKind == GET_ENTRY || entryKind == SET_ENTRY || isAsyncMethod) {
                        pname = objliteralProperty();
                        if (pname == null) {
                            reportError("msg.bad.prop");
                        }
                        consumeToken();
                    }
                    if (pname == null) {
                        propertyName = null;
                    } else {
                        propertyName = ts.getString();
                        functionIsAsync = isAsyncMethod;
                        // shorthand method definition
                        ObjectProperty objectProp =
                                methodDefinition(
                                        ppos,
                                        propStart,
                                        pname,
                                        entryKind,
                                        pname instanceof GeneratorMethodDefinition,
                                        true);
                        pname.setJsDocNode(jsdocNode);
                        elems.add(objectProp);
                    }
                } else {
                    boolean protoSetter =
                            peeked == Token.COLON
                                    && "__proto__".equals(propertyName)
                                    && (pname instanceof Name || pname instanceof StringLiteral);
                    if (protoSetter) {
                        ++protoDefinitions;
                    }
                    pname.setJsDocNode(jsdocNode);
                    ObjectProperty plain = plainProperty(pname, tt);
                    if (protoSetter) {
                        plain.putIntProp(Node.PROTO_SETTER_PROP, 1);
                    }
                    elems.add(plain);
                }
                if (pname instanceof GeneratorMethodDefinition && entryKind != METHOD_ENTRY) {
                    reportError("msg.bad.prop");
                }
            }

            if (this.inUseStrictDirective
                    && propertyName != null
                    && !(pname instanceof ComputedPropertyKey)
                    && compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
                switch (entryKind) {
                    case PROP_ENTRY:
                    case METHOD_ENTRY:
                        if (getterNames.contains(propertyName)
                                || setterNames.contains(propertyName)) {
                            addError("msg.dup.obj.lit.prop.strict", propertyName);
                        }
                        getterNames.add(propertyName);
                        setterNames.add(propertyName);
                        break;
                    case GET_ENTRY:
                        if (getterNames.contains(propertyName)) {
                            addError("msg.dup.obj.lit.prop.strict", propertyName);
                        }
                        getterNames.add(propertyName);
                        break;
                    case SET_ENTRY:
                        if (setterNames.contains(propertyName)) {
                            addError("msg.dup.obj.lit.prop.strict", propertyName);
                        }
                        setterNames.add(propertyName);
                        break;
                }
            }

            // Eat any dangling jsdoc in the property.
            getAndResetJsDoc();

            if (matchToken(Token.COMMA, true)) {
                afterComma = ts.tokenEnd;
            } else {
                break commaLoop;
            }
        }

        mustMatchToken(Token.RC, "msg.no.brace.prop", true);
        ObjectLiteral pn = new ObjectLiteral(pos, ts.tokenEnd - pos);
        // two __proto__: value definitions are a SyntaxError, unless the literal is a pattern
        // (ECMA-262 B.3.1)
        if (objectLiteralDestructuringDefault
                || (protoDefinitions > 1 && compilerEnv.isLatestEcmaScript())) {
            pn.putIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 1);
        }
        if (objJsdocNode != null) {
            pn.setJsDocNode(objJsdocNode);
        }
        pn.setElements(elems);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private AstNode objliteralProperty() throws IOException {
        AstNode pname;
        int tt = peekToken();
        switch (tt) {
            case Token.NAME:
                pname = createNameNode();
                break;

            case Token.STRING:
                pname = createStringLiteral();
                break;

            case Token.NUMBER:
            case Token.BIGINT:
                pname = createNumericLiteral(tt, true);
                break;

            case Token.DOTDOTDOT:
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    int pos = ts.tokenBeg;
                    nextToken();
                    int lineno = lineNumber();
                    int column = columnNumber();

                    AstNode exprNode = assignExpr();
                    pname = new Spread(pos, ts.tokenEnd - pos);
                    pname.setLineColumnNumber(lineno, column);
                    ((Spread) pname).setExpression(exprNode);
                } else {
                    reportError("msg.bad.prop");
                    return null;
                }
                break;

            case Token.LB:
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    int pos = ts.tokenBeg;
                    nextToken();
                    int lineno = lineNumber();
                    int column = columnNumber();
                    // the in operator is allowed in a computed key, also in a for head (under
                    // VERSION_ECMASCRIPT)
                    boolean savedInForInit = inForInit;
                    inForInit = savedInForInit && !compilerEnv.isLatestEcmaScript();
                    AstNode expr;
                    try {
                        expr = assignExpr();
                    } finally {
                        inForInit = savedInForInit;
                    }
                    if (peekToken() != Token.RB) {
                        reportError("msg.bad.prop");
                    }
                    nextToken();

                    pname = new ComputedPropertyKey(pos, ts.tokenEnd - pos);
                    pname.setLineColumnNumber(lineno, column);
                    ((ComputedPropertyKey) pname).setExpression(expr);
                } else {
                    reportError("msg.bad.prop");
                    return null;
                }
                break;

            case Token.MUL:
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    int pos = ts.tokenBeg;
                    nextToken();
                    int lineno = lineNumber();
                    int column = columnNumber();
                    pname = objliteralProperty();
                    if (pname == null) {
                        reportError("msg.bad.prop");
                        return null;
                    }

                    pname = new GeneratorMethodDefinition(pos, ts.tokenEnd - pos, pname);
                    pname.setLineColumnNumber(lineno, column);
                } else {
                    reportError("msg.bad.prop");
                    return null;
                }
                break;

            default:
                if (compilerEnv.isReservedKeywordAsIdentifier()
                        && TokenStream.isKeyword(
                                ts.getString(),
                                compilerEnv.getLanguageVersion(),
                                inUseStrictDirective)) {
                    // convert keyword to property name, e.g. ({if: 1})
                    pname = createNameNode();
                    break;
                }
                return null;
        }

        return pname;
    }

    private ObjectProperty plainProperty(AstNode property, int ptt) throws IOException {
        // Support, e.g., |var {x, y} = o| as destructuring shorthand
        // for |var {x: x, y: y} = o|, as implemented in spidermonkey JS 1.8.
        int tt = peekToken();
        if ((tt == Token.COMMA || tt == Token.RC)
                && (ptt == Token.NAME
                        || (ptt == Token.LET
                                && compilerEnv.isLatestEcmaScript()
                                && !inUseStrictDirective))
                && compilerEnv.getLanguageVersion() >= Context.VERSION_1_8) {
            if (!inDestructuringAssignment
                    && compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
                reportError("msg.bad.object.init");
            }
            AstNode nn = new Name(property.getPosition(), property.getString());
            ObjectProperty pn = new ObjectProperty();
            pn.setKeyAndValue(property, nn);
            return pn;
        } else if (tt == Token.ASSIGN) {
            /* we're in destructuring with defaults in a object literal; treat defaults as values */
            ObjectProperty pn = new ObjectProperty();
            consumeToken(); // consume the `=`
            Assignment defaultValue = new Assignment(property, assignExpr());
            defaultValue.setType(Token.ASSIGN);
            pn.setKeyAndValue(property, defaultValue);
            return pn;
        }
        mustMatchToken(Token.COLON, "msg.no.colon.prop", true);
        ObjectProperty pn = new ObjectProperty();
        pn.setKeyAndValue(property, assignExpr());
        return pn;
    }

    private ObjectProperty methodDefinition(
            int pos,
            int sourceStart,
            AstNode propName,
            int entryKind,
            boolean isGenerator,
            boolean isShorthand)
            throws IOException {
        methodIsGenerator = isGenerator && entryKind == METHOD_ENTRY;
        FunctionNode fn = function(FunctionNode.FUNCTION_EXPRESSION, true);
        if (compilerEnv.isLatestEcmaScript()) {
            // the source text of a method starts with its get/set/* prefix and name
            fn.setRawSourceBounds(sourceStart, fn.getRawSourceEnd());
        }
        if (compilerEnv.isLatestEcmaScript()) {
            // a getter has no parameter, a setter exactly one (not a rest parameter)
            if (entryKind == GET_ENTRY && !fn.getParams().isEmpty()) {
                reportError("msg.getter.params");
            } else if (entryKind == SET_ENTRY
                    && (fn.getParams().size() != 1 || fn.hasRestParameter())) {
                reportError("msg.setter.params");
            }
        }
        // We've already parsed the function name, so fn should be anonymous.
        Name name = fn.getFunctionName();
        if (name != null && name.length() != 0) {
            reportError("msg.bad.prop");
        }
        ObjectProperty pn = new ObjectProperty(pos);
        switch (entryKind) {
            case GET_ENTRY:
                pn.setIsGetterMethod();
                fn.setFunctionIsGetterMethod();
                if (compilerEnv.isLatestEcmaScript()) {
                    // like a method: no prototype, not a constructor
                    fn.setIsShorthand();
                }
                break;
            case SET_ENTRY:
                pn.setIsSetterMethod();
                fn.setFunctionIsSetterMethod();
                if (compilerEnv.isLatestEcmaScript()) {
                    fn.setIsShorthand();
                }
                break;
            case METHOD_ENTRY:
                pn.setIsNormalMethod();
                fn.setFunctionIsNormalMethod();
                if (isGenerator) {
                    fn.setIsES6Generator();
                }
                if (isShorthand) {
                    fn.setIsShorthand();
                }
                break;
        }
        int end = getNodeEnd(fn);
        pn.setKeyAndValue(propName, fn);
        pn.setLength(end - pos);
        return pn;
    }

    private Name createNameNode() {
        return createNameNode(false, Token.NAME);
    }

    /**
     * Create a {@code Name} node using the token info from the last scanned name. In some cases we
     * need to either synthesize a name node, or we lost the name token information by peeking. If
     * the {@code token} parameter is not {@link Token#NAME}, then we use token info saved in
     * instance vars.
     */
    private Name createNameNode(boolean checkActivation, int token) {
        int beg = ts.tokenBeg;
        String s = ts.getString();
        int lineno = lineNumber();
        int column = columnNumber();
        if (!"".equals(prevNameTokenString)) {
            beg = prevNameTokenStart;
            s = prevNameTokenString;
            lineno = prevNameTokenLineno;
            column = prevNameTokenColumn;
            prevNameTokenStart = 0;
            prevNameTokenString = "";
            prevNameTokenLineno = 0;
            prevNameTokenColumn = 0;
        }
        if (s == null) {
            if (compilerEnv.isIdeMode()) {
                s = "";
            } else {
                codeBug();
            }
        }
        Name name = new Name(beg, s);
        name.setLineColumnNumber(lineno, column);
        if (checkActivation) {
            checkActivationName(s, token);
        }
        return name;
    }

    private StringLiteral createStringLiteral() {
        if (ts.stringHadLegacyEscape) {
            if (inUseStrictDirective && compilerEnv.isLatestEcmaScript()) {
                reportError("msg.no.octal.escape.strict");
            }
            legacyEscapeInPrologue = true;
        }
        int pos = ts.tokenBeg, end = ts.tokenEnd;
        StringLiteral s = new StringLiteral(pos, end - pos);
        s.setLineColumnNumber(lineNumber(), columnNumber());
        s.setValue(ts.getString());
        s.setQuoteCharacter(ts.getQuoteChar());
        return s;
    }

    private AstNode templateLiteral(boolean isTaggedLiteral) throws IOException {
        if (currentToken != Token.TEMPLATE_LITERAL) codeBug();
        int pos = ts.tokenBeg, end = ts.tokenEnd, lineno = lineNumber(), column = columnNumber();
        List<AstNode> elements = new ArrayList<>();
        TemplateLiteral pn = new TemplateLiteral(pos);

        int posChars = ts.tokenBeg + 1;
        int tt = ts.readTemplateLiteral(isTaggedLiteral);
        while (tt == Token.TEMPLATE_LITERAL_SUBST) {
            elements.add(createTemplateLiteralCharacters(posChars));
            elements.add(expr(false));
            mustMatchToken(Token.RC, "msg.syntax", true);
            posChars = ts.tokenBeg + 1;
            tt = ts.readTemplateLiteral(isTaggedLiteral);
        }
        if (tt == Token.ERROR) {
            return makeErrorNode();
        }
        assert tt == Token.TEMPLATE_LITERAL;
        elements.add(createTemplateLiteralCharacters(posChars));
        end = ts.tokenEnd;
        pn.setElements(elements);
        pn.setLength(end - pos);
        pn.setLineColumnNumber(lineno, column);

        return pn;
    }

    private TemplateCharacters createTemplateLiteralCharacters(int pos) {
        TemplateCharacters chars = new TemplateCharacters(pos, ts.tokenEnd - pos - 1);
        chars.setValue(ts.getString());
        chars.setRawValue(ts.getRawString());
        return chars;
    }

    private AstNode createNumericLiteral(int tt, boolean isProperty) {
        String s = ts.getString();
        if (this.inUseStrictDirective && ts.isNumericOldOctal()) {
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6 || !isProperty) {
                if (tt == Token.BIGINT) {
                    reportError("msg.no.old.octal.bigint");
                } else {
                    reportError("msg.no.old.octal.strict");
                }
            }
        }
        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6 || !isProperty) {
            if (ts.isNumericBinary()) {
                s = "0b" + s;
            } else if (ts.isNumericOldOctal()) {
                s = "0" + s;
            } else if (ts.isNumericOctal()) {
                s = "0o" + s;
            } else if (ts.isNumericHex()) {
                s = "0x" + s;
            }
        }

        AstNode result;
        if (tt == Token.BIGINT) {
            result = new BigIntLiteral(ts.tokenBeg, s + "n", ts.getBigInt());
        } else {
            result = new NumberLiteral(ts.tokenBeg, s, ts.getNumber());
        }
        result.setLineColumnNumber(lineNumber(), columnNumber());
        return result;
    }

    protected void checkActivationName(String name, int token) {
        if ("arguments".equals(name) && currentScriptOrFn instanceof FunctionNode) {
            // If there is a usage of "arguments" we need to initialize it. However,
            // we might not be in a function body, because we could be inside a function's
            // default arguments. So, we do this check first, before the "insideFunctionBody"
            FunctionNode fn = (FunctionNode) currentScriptOrFn;
            fn.setRequiresArgumentObject();
            if (insideFunctionParams()
                    && fn.getFunctionType() != FunctionNode.ARROW_FUNCTION
                    && compilerEnv.isLatestEcmaScript()) {
                fn.setRequiresActivation();
            }
        }

        if (!insideFunctionBody()) {
            return;
        }
        boolean activation = false;
        if ("arguments".equals(name)
                &&
                // An arrow function not generate arguments. So it not need activation.
                ((FunctionNode) currentScriptOrFn).getFunctionType()
                        != FunctionNode.ARROW_FUNCTION) {
            // under VERSION_ECMASCRIPT, IRFactory decides once the function is parsed: a strict
            // function can keep its arguments object in a register
            activation = !compilerEnv.isLatestEcmaScript();
        } else if (compilerEnv.getActivationNames() != null
                && compilerEnv.getActivationNames().contains(name)) {
            activation = true;
        }
        if (activation) {
            setRequiresActivation();
        }
    }

    protected void setRequiresActivation() {
        if (insideFunctionBody()) {
            ((FunctionNode) currentScriptOrFn).setRequiresActivation();
        }
    }

    /**
     * The parameters of an arrow function were parsed as an expression of the enclosing function: a
     * direct eval in them is a call in the parameters of the arrow (which needs an activation).
     */
    private void markEvalInArrowParams(AstNode params) {
        params.visit(
                node -> {
                    if (node instanceof FunctionNode) {
                        return false;
                    }
                    if (node instanceof FunctionCall) {
                        AstNode target = ((FunctionCall) node).getTarget();
                        if (target instanceof Name
                                && "eval".equals(((Name) target).getIdentifier())) {
                            ((FunctionCall) node).markInParameters();
                            setRequiresActivation();
                            setRequiresArgumentObject();
                        }
                    }
                    return true;
                });
    }

    private void checkCallRequiresActivation(AstNode pn) {
        if ((pn.getType() == Token.NAME && "eval".equals(((Name) pn).getIdentifier()))
                || (pn.getType() == Token.GETPROP
                        && "eval".equals(((PropertyGet) pn).getProperty().getIdentifier()))) {
            setRequiresActivation();
            setRequiresArgumentObject();
        }
    }

    protected void setIsGenerator() {
        if (insideFunctionBody()) {
            ((FunctionNode) currentScriptOrFn).setIsGenerator();
        }
    }

    private void setRequiresArgumentObject() {
        if (insideFunctionBody()) {
            ((FunctionNode) currentScriptOrFn).setRequiresArgumentObject();
        }
    }

    private void checkBadIncDec(UpdateExpression expr) {
        if (compilerEnv.isLatestEcmaScript()) {
            AstNode target = expr.getOperand();
            if (!isValidAssignmentTarget(target, false, false)) {
                reportError(expr.getType() == Token.INC ? "msg.bad.incr" : "msg.bad.decr");
            } else if (inUseStrictDirective && removeParens(target) instanceof Name) {
                String id = ((Name) removeParens(target)).getIdentifier();
                if ("eval".equals(id) || "arguments".equals(id)) {
                    reportError("msg.bad.id.strict", id);
                }
            }
            return;
        }
        AstNode op = removeParens(expr.getOperand());
        int tt = op.getType();
        if (!(tt == Token.NAME
                || tt == Token.GETPROP
                || tt == Token.GETELEM
                || tt == Token.GET_REF
                || tt == Token.CALL))
            reportError(expr.getType() == Token.INC ? "msg.bad.incr" : "msg.bad.decr");
    }

    private ErrorNode makeErrorNode() {
        ErrorNode pn = new ErrorNode(ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        return pn;
    }

    // Return end of node.  Assumes node does NOT have a parent yet.
    private static int nodeEnd(AstNode node) {
        return node.getPosition() + node.getLength();
    }

    private void saveNameTokenData(int pos, String name, int lineno, int column) {
        prevNameTokenStart = pos;
        prevNameTokenString = name;
        prevNameTokenLineno = lineno;
        prevNameTokenColumn = column;
    }

    /**
     * Return the file offset of the beginning of the input source line containing the passed
     * position.
     *
     * @param pos an offset into the input source stream. If the offset is negative, it's converted
     *     to 0, and if it's beyond the end of the source buffer, the last source position is used.
     * @return the offset of the beginning of the line containing pos (i.e. 1+ the offset of the
     *     first preceding newline). Returns -1 if the {@link CompilerEnvirons} is not set to
     *     ide-mode, and {@link #parse(Reader,String,int)} was used.
     */
    private int lineBeginningFor(int pos) {
        if (sourceChars == null) {
            return -1;
        }
        if (pos <= 0) {
            return 0;
        }
        char[] buf = sourceChars;
        if (pos >= buf.length) {
            pos = buf.length - 1;
        }
        while (--pos >= 0) {
            char c = buf[pos];
            if (ScriptRuntime.isJSLineTerminator(c)) {
                return pos + 1; // want position after the newline
            }
        }
        return 0;
    }

    private void warnMissingSemi(int pos, int end) {
        // Should probably change this to be a CompilerEnvirons setting,
        // with an enum Never, Always, Permissive, where Permissive means
        // don't warn for 1-line functions like function (s) {return x+2}
        if (compilerEnv.isStrictMode()) {
            int[] linep = new int[2];
            String line = ts.getLine(end, linep);
            // this code originally called lineBeginningFor() and in order to
            // preserve its different line-offset handling, we need to special
            // case ide-mode here
            int beg = compilerEnv.isIdeMode() ? Math.max(pos, end - linep[1]) : pos;
            if (line != null) {
                addStrictWarning("msg.missing.semi", "", beg, end - beg, linep[0], line, linep[1]);
            } else {
                // no line information available, report warning at current line
                addStrictWarning("msg.missing.semi", "", beg, end - beg);
            }
        }
    }

    private void warnTrailingComma(int pos, List<?> elems, int commaPos) {
        if (compilerEnv.getWarnTrailingComma()) {
            // back up from comma to beginning of line or array/objlit
            if (!elems.isEmpty()) {
                pos = ((AstNode) elems.get(0)).getPosition();
            }
            pos = Math.max(pos, lineBeginningFor(commaPos));
            addWarning("msg.extra.trailing.comma", pos, commaPos - pos);
        }
    }

    // helps reduce clutter in the already-large function() method
    protected class PerFunctionVariables {
        private ScriptNode savedCurrentScriptOrFn;
        private Scope savedCurrentScope;
        private int savedEndFlags;
        private boolean savedInForInit;
        private Map<String, LabeledStatement> savedLabelSet;
        private List<Loop> savedLoopSet;
        private List<Jump> savedLoopAndSwitchSet;
        private boolean savedHasUndefinedBeenRedefined;
        private int savedNonArrowFunctionDepth;
        private boolean savedInFieldInitializer;
        private boolean savedInStaticBlock;
        private boolean savedInParenthesizedCover;
        private int savedPendingCoverInitializedNames;

        PerFunctionVariables(FunctionNode fnNode) {
            savedInParenthesizedCover = Parser.this.inParenthesizedCover;
            savedPendingCoverInitializedNames = Parser.this.pendingCoverInitializedNames;
            Parser.this.inParenthesizedCover = false;
            savedNonArrowFunctionDepth = Parser.this.nonArrowFunctionDepth;
            savedInFieldInitializer = Parser.this.inFieldInitializer;
            savedInStaticBlock = Parser.this.inStaticBlock;
            Parser.this.inStaticBlock = false;
            if (fnNode.getFunctionType() != FunctionNode.ARROW_FUNCTION) {
                Parser.this.nonArrowFunctionDepth++;
                // an arrow function in an initializer is still in it: arguments is an error there
                Parser.this.inFieldInitializer = fnNode.isFieldInitializer();
            }
            savedCurrentScriptOrFn = Parser.this.currentScriptOrFn;
            Parser.this.currentScriptOrFn = fnNode;

            savedCurrentScope = Parser.this.currentScope;
            Parser.this.currentScope = fnNode;

            savedLabelSet = Parser.this.labelSet;
            Parser.this.labelSet = null;

            savedLoopSet = Parser.this.loopSet;
            Parser.this.loopSet = null;

            savedLoopAndSwitchSet = Parser.this.loopAndSwitchSet;
            Parser.this.loopAndSwitchSet = null;

            savedEndFlags = Parser.this.endFlags;
            Parser.this.endFlags = 0;

            savedInForInit = Parser.this.inForInit;
            Parser.this.inForInit = false;

            savedHasUndefinedBeenRedefined = Parser.this.hasUndefinedBeenRedefined;
            // we want to inherit the current value
        }

        void restore() {
            Parser.this.inFieldInitializer = savedInFieldInitializer;
            Parser.this.inStaticBlock = savedInStaticBlock;
            Parser.this.currentScriptOrFn = savedCurrentScriptOrFn;
            Parser.this.currentScope = savedCurrentScope;
            Parser.this.labelSet = savedLabelSet;
            Parser.this.loopSet = savedLoopSet;
            Parser.this.loopAndSwitchSet = savedLoopAndSwitchSet;
            Parser.this.endFlags = savedEndFlags;
            Parser.this.inForInit = savedInForInit;
            Parser.this.hasUndefinedBeenRedefined = savedHasUndefinedBeenRedefined;
            Parser.this.nonArrowFunctionDepth = savedNonArrowFunctionDepth;
            Parser.this.inParenthesizedCover = savedInParenthesizedCover;
            Parser.this.pendingCoverInitializedNames = savedPendingCoverInitializedNames;
        }
    }

    PerFunctionVariables createPerFunctionVariables(FunctionNode fnNode) {
        return new PerFunctionVariables(fnNode);
    }

    /**
     * Given a destructuring assignment with a left hand side parsed as an array or object literal
     * and a right hand side expression, rewrite as a series of assignments to the variables defined
     * in left from property accesses to the expression on the right.
     *
     * @param type declaration type: Token.VAR or Token.LET or -1
     * @param left array or object literal containing NAME nodes for variables to assign
     * @param right expression to assign from
     * @return expression that performs a series of assignments to the variables defined in left
     */
    Node createDestructuringAssignment(
            int type,
            Node left,
            Node right,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter) {
        String tempName = currentScriptOrFn.getNextTempName();
        Node result =
                destructuringAssignmentHelper(
                        type,
                        left,
                        right,
                        tempName,
                        defaultValue,
                        transformer,
                        isFunctionParameter);
        Node comma = result.getLastChild();
        comma.addChildToBack(createName(tempName));
        return result;
    }

    Node createDestructuringAssignment(
            int type, Node left, Node right, AstNode defaultValue, Transformer transformer) {
        return createDestructuringAssignment(type, left, right, defaultValue, transformer, true);
    }

    Node createDestructuringAssignment(int type, Node left, Node right, Transformer transformer) {
        return createDestructuringAssignment(type, left, right, null, transformer, false);
    }

    Node createDestructuringAssignment(int type, Node left, Node right, AstNode defaultValue) {
        return createDestructuringAssignment(type, left, right, defaultValue, null, true);
    }

    /**
     * A pattern nested in another one: its bound names are also names of the enclosing pattern, so
     * that a let/const declaration declares them all.
     */
    private Node nestedDestructuring(
            List<String> destructuringNames,
            int variableType,
            Node left,
            Node right,
            String tempName,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter) {
        Node result =
                destructuringAssignmentHelper(
                        variableType,
                        left,
                        right,
                        tempName,
                        defaultValue,
                        transformer,
                        isFunctionParameter);
        Object names = result.getProp(Node.DESTRUCTURING_NAMES);
        if (names instanceof List) {
            for (Object name : (List<?>) names) {
                destructuringNames.add((String) name);
            }
        }
        return result;
    }

    Node destructuringAssignmentHelper(
            int variableType,
            Node left,
            Node right,
            String tempName,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter) {
        Scope result = createScopeNode(Token.LETEXPR, left.getLineno(), left.getColumn());
        Node letNode = new Node(Token.LET, createName(Token.NAME, tempName, right));
        result.addChildToFront(letNode);
        try {
            pushScope(result);
            defineSymbol(Token.LET, tempName, true);
        } finally {
            popScope();
        }
        Node comma = new Node(Token.COMMA);
        result.addChildToBack(comma);
        List<String> destructuringNames = new ArrayList<>();
        boolean empty = true;
        if (left instanceof ArrayLiteral) {
            Scope savedScope = destructuringScope;
            Node savedLet = destructuringLet;
            destructuringScope = result;
            destructuringLet = letNode;
            try {
                empty =
                        destructuringArray(
                                (ArrayLiteral) left,
                                variableType,
                                tempName,
                                comma,
                                destructuringNames,
                                defaultValue,
                                transformer,
                                isFunctionParameter);
            } finally {
                destructuringScope = savedScope;
                destructuringLet = savedLet;
            }
        } else if (left instanceof ObjectLiteral) {
            Scope savedScope = destructuringScope;
            Node savedLet = destructuringLet;
            destructuringScope = result;
            destructuringLet = letNode;
            try {
                empty =
                        destructuringObject(
                                (ObjectLiteral) left,
                                variableType,
                                tempName,
                                comma,
                                destructuringNames,
                                defaultValue,
                                transformer,
                                isFunctionParameter,
                                letNode,
                                result);
            } finally {
                destructuringScope = savedScope;
                destructuringLet = savedLet;
            }
        } else if (left.getType() == Token.GETPROP || left.getType() == Token.GETELEM) {
            switch (variableType) {
                case Token.CONST:
                case Token.LET:
                case Token.VAR:
                    reportError("msg.bad.assign.left");
            }
            comma.addChildToBack(simpleAssignment(left, createName(tempName), transformer));
        } else {
            reportError("msg.bad.assign.left");
        }
        if (empty) {
            // Don't want a COMMA node with no children. Just add a zero.
            comma.addChildToBack(createNumber(0));
        }

        result.putProp(Node.DESTRUCTURING_NAMES, destructuringNames);
        return result;
    }

    /**
     * An array pattern. From ES6 on, it follows the iterator protocol (ECMA-262 8.6.2
     * IteratorBindingInitialization): Token#ITER_OPEN, one Token#ITER_STEP per element or hole,
     * Token#ITER_REST for a rest element, and Token#ITER_CLOSE at the end; older versions read the
     * value by index.
     */
    boolean destructuringArray(
            ArrayLiteral array,
            int variableType,
            String tempName,
            Node parent,
            List<String> destructuringNames,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter) {
        if (!compilerEnv.isLatestEcmaScript()) {
            return destructuringArrayLegacy(
                    array,
                    variableType,
                    tempName,
                    parent,
                    destructuringNames,
                    defaultValue,
                    transformer,
                    isFunctionParameter);
        }
        boolean useIterator = true;
        int setOp = variableType == Token.CONST ? Token.SETCONST : Token.SETNAME;
        if (defaultValue != null) {
            setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
        }
        String iteratorName = null;
        if (useIterator) {
            iteratorName = defineTemp();
            Node open = new Node(Token.ITER_OPEN, createName(tempName));
            if (bindsNamesOnly(array, variableType, isFunctionParameter)) {
                // only the iterator itself can throw before it is done
                open.putIntProp(Node.UNGUARDED_ITERATOR_PROP, 1);
            }
            parent.addChildToBack(
                    new Node(Token.SETNAME, createName(Token.BINDNAME, iteratorName, null), open));
            ++iteratorOpenCount;
        }
        boolean empty = !useIterator;
        int index = 0;
        for (AstNode n : array.getElements()) {
            if (n.getType() == Token.EMPTY) {
                if (useIterator) {
                    parent.addChildToBack(new Node(Token.ITER_STEP, createName(iteratorName)));
                }
                index++;
                continue;
            }

            if (n instanceof Spread) {
                // [...rest] = [1, 2, 3]; the rest element must be the last
                if (index < array.getElements().size() - 1) {
                    reportError("msg.parm.after.rest");
                }
                AstNode restTarget = ((Spread) n).getExpression();
                Node restValue;
                if (useIterator) {
                    restValue = new Node(Token.ITER_REST, createName(iteratorName));
                } else {
                    restValue =
                            new Node(
                                    Token.CALL,
                                    new Node(
                                            Token.GETPROP,
                                            createName(tempName),
                                            Node.newString("slice")));
                    restValue.addChildToBack(createNumber(index));
                }
                if (isMemberTarget(restTarget)) {
                    // the reference is evaluated before the remaining values are collected
                    parent.addChildToBack(simpleAssignment(restTarget, restValue, transformer));
                } else if (restTarget.getType() == Token.NAME) {
                    String name = restTarget.getString();
                    parent.addChildToBack(destructuringSet(setOp, name, restValue, variableType));
                    if (variableType != -1) {
                        defineSymbol(variableType, name, true);
                        destructuringNames.add(name);
                    }
                } else {
                    // [...[x, y]] or [...{length}]
                    parent.addChildToBack(
                            nestedDestructuring(
                                    destructuringNames,
                                    variableType,
                                    restTarget,
                                    restValue,
                                    currentScriptOrFn.getNextTempName(),
                                    null,
                                    transformer,
                                    isFunctionParameter));
                }
                index++;
                empty = false;
                continue;
            }

            AstNode target = n.getType() == Token.ASSIGN ? ((Assignment) n).getLeft() : n;
            if (useIterator && isMemberTarget(target)) {
                // the reference (object and key) is evaluated before the iterator is stepped
                Node step = new Node(Token.ITER_STEP, createName(iteratorName));
                Node value = step;
                if (n.getType() == Token.ASSIGN) {
                    value = defaultedValue(step, ((Assignment) n).getRight(), transformer);
                }
                parent.addChildToBack(simpleAssignment(target, value, transformer));
                index++;
                empty = false;
                continue;
            }

            if (useIterator && n.getType() == Token.NAME) {
                // [x] = a: the binding is resolved before the iterator is stepped
                String name = n.getString();
                parent.addChildToBack(
                        destructuringSet(
                                setOp,
                                name,
                                new Node(Token.ITER_STEP, createName(iteratorName)),
                                variableType));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
                index++;
                empty = false;
                continue;
            }

            Node rightElem;
            if (useIterator) {
                String elemName = defineTemp();
                parent.addChildToBack(
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, elemName, null),
                                new Node(Token.ITER_STEP, createName(iteratorName))));
                rightElem = createName(elemName);
            } else {
                rightElem = new Node(Token.GETELEM, createName(tempName), createNumber(index));
            }

            if (n.getType() == Token.NAME) {
                /* [x] = [1] */
                String name = n.getString();
                parent.addChildToBack(destructuringSet(setOp, name, rightElem, variableType));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
            } else if (n.getType() == Token.ASSIGN) {
                /* [x = 1] = [2] */
                processDestructuringDefaults(
                        variableType,
                        parent,
                        destructuringNames,
                        (Assignment) n,
                        rightElem,
                        setOp,
                        transformer,
                        isFunctionParameter);
            } else {
                parent.addChildToBack(
                        nestedDestructuring(
                                destructuringNames,
                                variableType,
                                n,
                                rightElem,
                                currentScriptOrFn.getNextTempName(),
                                null,
                                transformer,
                                isFunctionParameter));
            }
            index++;
            empty = false;
        }
        if (useIterator) {
            parent.addChildToBack(new Node(Token.ITER_CLOSE, createName(iteratorName)));
        }
        return empty;
    }

    /**
     * Whether an array pattern only initializes parameters or let or const bindings by name, holes
     * and a rest element included: such an initialization cannot throw.
     */
    private static boolean bindsNamesOnly(
            ArrayLiteral array, int variableType, boolean isFunctionParameter) {
        if (variableType != Token.LET && variableType != Token.CONST && !isFunctionParameter) {
            return false;
        }
        for (AstNode n : array.getElements()) {
            AstNode target = n instanceof Spread ? ((Spread) n).getExpression() : n;
            if (target.getType() != Token.NAME && target.getType() != Token.EMPTY) {
                return false;
            }
        }
        return true;
    }

    /**
     * The array patterns of stock Rhino, before VERSION_ECMASCRIPT: elements are read by index and
     * a rest element is a slice, except for the parameters of code from VERSION_ES6 on, which call
     * the iterator of the global Symbol for each non-hole element and close it if it is not done.
     * The default value of a parameter is applied before the first element.
     */
    private boolean destructuringArrayLegacy(
            ArrayLiteral array,
            int variableType,
            String tempName,
            Node parent,
            List<String> destructuringNames,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter) {
        boolean empty = true;
        int setOp = variableType == Token.CONST ? Token.SETCONST : Token.SETNAME;
        int index = 0;
        boolean defaultValuesSetup = false;
        String iteratorName = null;
        String lastResultName = null;
        for (AstNode n : array.getElements()) {
            if (n.getType() == Token.EMPTY) {
                index++;
                continue;
            }
            if (defaultValue != null && !defaultValuesSetup) {
                setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                defaultValuesSetup = true;
            }
            if (isFunctionParameter
                    && iteratorName == null
                    && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                // iterator = temp[Symbol.iterator]()
                iteratorName = defineTemp();
                lastResultName = defineTemp();
                Node getIteratorMethod =
                        new Node(
                                Token.GETELEM,
                                createName(tempName),
                                new Node(
                                        Token.GETPROP,
                                        createName("Symbol"),
                                        Node.newString("iterator")));
                parent.addChildToBack(
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, iteratorName, null),
                                new Node(Token.CALL, getIteratorMethod)));
                empty = false;
            }
            Node rightElem;
            if (iteratorName != null) {
                // lastResult = iterator.next(); elem = lastResult.value
                Node callNext =
                        new Node(
                                Token.CALL,
                                new Node(
                                        Token.GETPROP,
                                        createName(iteratorName),
                                        Node.newString("next")));
                parent.addChildToBack(
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, lastResultName, null),
                                callNext));
                String elemTempName = defineTemp();
                parent.addChildToBack(
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, elemTempName, null),
                                new Node(
                                        Token.GETPROP,
                                        createName(lastResultName),
                                        Node.newString("value"))));
                rightElem = createName(elemTempName);
            } else {
                rightElem = new Node(Token.GETELEM, createName(tempName), createNumber(index));
            }
            if (n.getType() == Token.NAME) {
                String name = n.getString();
                parent.addChildToBack(destructuringSet(setOp, name, rightElem, variableType));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
            } else if (n.getType() == Token.ASSIGN) {
                processDestructuringDefaults(
                        variableType,
                        parent,
                        destructuringNames,
                        (Assignment) n,
                        rightElem,
                        setOp,
                        transformer,
                        isFunctionParameter);
            } else if (n instanceof Spread) {
                if (index < array.getElements().size() - 1) {
                    reportError("msg.parm.after.rest");
                }
                AstNode restTarget = ((Spread) n).getExpression();
                Node sliceCall =
                        new Node(
                                Token.CALL,
                                new Node(
                                        Token.GETPROP,
                                        createName(tempName),
                                        Node.newString("slice")));
                sliceCall.addChildToBack(createNumber(index));
                if (restTarget.getType() == Token.NAME) {
                    String name = restTarget.getString();
                    parent.addChildToBack(destructuringSet(setOp, name, sliceCall, variableType));
                    if (variableType != -1) {
                        defineSymbol(variableType, name, true);
                        destructuringNames.add(name);
                    }
                } else {
                    parent.addChildToBack(
                            nestedDestructuring(
                                    destructuringNames,
                                    variableType,
                                    restTarget,
                                    sliceCall,
                                    currentScriptOrFn.getNextTempName(),
                                    null,
                                    transformer,
                                    isFunctionParameter));
                }
            } else {
                parent.addChildToBack(
                        nestedDestructuring(
                                destructuringNames,
                                variableType,
                                n,
                                rightElem,
                                currentScriptOrFn.getNextTempName(),
                                null,
                                transformer,
                                isFunctionParameter));
            }
            index++;
            empty = false;
        }
        if (iteratorName != null) {
            // !lastResult.done ? ((f = iterator.return) !== undefined ? f.call(iterator)
            //     : undefined) : undefined
            String returnMethodName = defineTemp();
            Node notDone =
                    new Node(
                            Token.NOT,
                            new Node(
                                    Token.GETPROP,
                                    createName(lastResultName),
                                    Node.newString("done")));
            Node assignReturn =
                    new Node(
                            Token.SETNAME,
                            createName(Token.BINDNAME, returnMethodName, null),
                            new Node(
                                    Token.GETPROP,
                                    createName(iteratorName),
                                    Node.newString("return")));
            Node notUndefined = new Node(Token.NE, assignReturn, new Node(Token.UNDEFINED));
            Node callReturn =
                    new Node(
                            Token.CALL,
                            new Node(
                                    Token.GETPROP,
                                    createName(returnMethodName),
                                    Node.newString("call")));
            callReturn.addChildToBack(createName(iteratorName));
            Node innerTernary =
                    new Node(Token.HOOK, notUndefined, callReturn, new Node(Token.UNDEFINED));
            parent.addChildToBack(
                    new Node(Token.HOOK, notDone, innerTernary, new Node(Token.UNDEFINED)));
        }
        return empty;
    }

    /**
     * The store of a destructuring target name; in a destructuring assignment (variableType -1), it
     * is subject to the temporal dead zone of the binding it resolves to.
     */
    private Node destructuringSet(int setOp, String name, Node value, int variableType) {
        Node set = new Node(setOp, createName(Token.BINDNAME, name, null), value);
        if (variableType == -1) {
            set.putIntProp(Node.DESTRUCTURING_TARGET_PROP, 1);
            if (inUseStrictDirective
                    && ("eval".equals(name) || "arguments".equals(name))
                    && compilerEnv.isLatestEcmaScript()) {
                // 13.15.5.1: an assignment target must not be eval or arguments in strict code
                reportError("msg.bad.id.strict", name);
            }
        }
        return set;
    }

    /** An assignment target that is a property reference: obj.p or obj[k]. */
    private static boolean isMemberTarget(Node target) {
        return target instanceof PropertyGet || target instanceof ElementGet;
    }

    /**
     * The value of an element with a default, evaluated once: <code>($e = value) === undefined ?
     * default : $e</code>.
     */
    private Node defaultedValue(Node value, AstNode defaultValue, Transformer transformer) {
        String elemName = defineTemp();
        Node dflt = transformer != null ? transformer.transform(defaultValue) : defaultValue;
        return new Node(
                Token.HOOK,
                new Node(
                        Token.SHEQ,
                        new KeywordLiteral().setType(Token.UNDEFINED),
                        new Node(Token.SETNAME, createName(Token.BINDNAME, elemName, null), value)),
                dflt,
                createName(elemName));
    }

    /** Whether the peeked RESERVED token is the class keyword (ES6 and later). */
    private boolean isClassKeyword() {
        // classes are a feature of VERSION_ECMASCRIPT: class stays a reserved word before
        return compilerEnv.isLatestEcmaScript() && "class".equals(ts.getString());
    }

    /** A class declaration: a let binding of the class name, initialized by the class. */
    private AstNode classDeclaration() throws IOException {
        int pos = ts.tokenBeg, lineno = ts.getLineno(), column = ts.getTokenColumn();
        ClassNode cls = classDefinition(true);
        VariableDeclaration decl = new VariableDeclaration(pos, cls.getLength());
        decl.setType(Token.LET);
        VariableInitializer vi = new VariableInitializer(pos, cls.getLength());
        Name name = cls.getClassName();
        Name target = new Name(name.getPosition(), name.getIdentifier());
        target.setLineColumnNumber(name.getLineno(), name.getColumn());
        vi.setTarget(target);
        vi.setInitializer(cls);
        vi.setType(Token.LET);
        vi.setLineColumnNumber(lineno, column);
        decl.addVariable(vi);
        decl.setIsStatement(true);
        decl.setLineColumnNumber(lineno, column);
        Symbol symbol = currentScope.getSymbol(name.getIdentifier());
        if (symbol != null) {
            // the temporal dead zone of the binding ends with the class
            symbol.setNode(vi);
        }
        return decl;
    }

    /**
     * A class (ECMA-262 15.7): the class keyword is the peeked token. Everything in a class is
     * strict code. The name is also bound, immutably, in a scope around the class body.
     */
    private ClassNode classDefinition(boolean isStatement) throws IOException {
        consumeToken();
        int pos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        ClassNode cls = new ClassNode(pos);
        cls.setLineColumnNumber(lineno, column);
        boolean savedStrict = inUseStrictDirective;
        inUseStrictDirective = true;
        try {
            if (matchToken(Token.NAME, true)) {
                Name name = createNameNode(true, Token.NAME);
                String id = name.getIdentifier();
                if ("eval".equals(id) || "arguments".equals(id)) {
                    reportError("msg.bad.id.strict", id);
                }
                cls.setClassName(name);
                if (isStatement) {
                    defineSymbol(Token.LET, id);
                }
            } else if (isStatement) {
                reportError("msg.class.no.name");
            }
            if (peekToken() == Token.RESERVED && "extends".equals(ts.getString())) {
                consumeToken();
                // the { after the heritage starts the class body (not a new initializer)
                boolean savedInClassHeritage = inClassHeritage;
                inClassHeritage = true;
                try {
                    cls.setHeritage(memberExpr(true));
                } finally {
                    inClassHeritage = savedInClassHeritage;
                }
            }
            mustMatchToken(Token.LC, "msg.no.brace.class", true);

            Scope scope = createScopeNode(Token.LETEXPR, lineno, column);
            cls.setScope(scope);
            pushScope(scope);
            try {
                if (cls.getClassName() != null) {
                    // immutable: assigning it in the (strict) class code is a TypeError
                    defineSymbol(Token.CONST, cls.getClassName().getIdentifier(), true);
                    // in its temporal dead zone until the class is defined (heritage, keys)
                    scope.getSymbol(cls.getClassName().getIdentifier()).setNode(cls);
                }
                privateScope = new PrivateScope(privateScope);
                try {
                    classBody(cls, pos);
                } finally {
                    PrivateScope classScope = privateScope;
                    privateScope = classScope.outer;
                    resolvePrivateReferences(classScope);
                }
            } finally {
                popScope();
            }
            mustMatchToken(Token.RC, "msg.no.brace.class", true);
            cls.setSourceBounds(pos, ts.tokenEnd);
            cls.setLength(ts.tokenEnd - pos);
            if (cls.getConstructor() != null) {
                // the source text of the class is that of its constructor
                cls.getConstructor().setRawSourceBounds(pos, ts.tokenEnd);
            }
        } finally {
            inUseStrictDirective = savedStrict;
        }
        return cls;
    }

    private void classBody(ClassNode cls, int pos) throws IOException {
        ObjectLiteral prototypeMembers = new ObjectLiteral(pos);
        ObjectLiteral staticMembers = new ObjectLiteral(pos);
        List<AbstractObjectProperty> protoElems = new ArrayList<>();
        List<AbstractObjectProperty> staticElems = new ArrayList<>();
        // constructor is the first property of the prototype (ClassDefinition sets its value)
        ObjectProperty constructorSlot = new ObjectProperty(pos);
        constructorSlot.setKeyAndValue(
                new Name(pos, "constructor"), new KeywordLiteral(pos, 0, Token.UNDEFINED));
        protoElems.add(constructorSlot);
        for (; ; ) {
            int tt = peekToken();
            if (tt == Token.SEMI) {
                consumeToken();
                continue;
            }
            if (tt == Token.RC || tt == Token.EOF || tt == Token.ERROR) {
                break;
            }
            boolean isStatic = false;
            if ((tt == Token.NAME || tt == Token.RESERVED) && "static".equals(ts.getString())) {
                boolean escaped = ts.nameHadEscape;
                consumeToken();
                int staticPos = ts.tokenBeg;
                int next = peekToken();
                if (next == Token.LC) {
                    if (escaped) {
                        reportError("msg.invalid.escape");
                    }
                    staticBlock(cls, staticElems);
                    continue;
                }
                if (next == Token.LP || isFieldEnd("static")) {
                    // a method or a field named static
                    Name name = new Name(staticPos, "static");
                    classMember(cls, name, staticPos, protoElems, false, true);
                    continue;
                }
                if (escaped) {
                    // a keyword cannot contain escapes
                    reportError("msg.invalid.escape");
                }
                isStatic = true;
            }
            int propStart = ts.tokenBeg;
            AstNode pname = classElementName();
            if (pname == null || pname instanceof Spread) {
                reportError("msg.bad.prop");
                break;
            }
            classMember(
                    cls, pname, propStart, isStatic ? staticElems : protoElems, isStatic, false);
        }
        prototypeMembers.setElements(protoElems);
        staticMembers.setElements(staticElems);
        cls.setPrototypeMembers(prototypeMembers);
        cls.setStaticMembers(staticMembers);
    }

    /** A method, getter or setter of a class; pname is the peeked (or synthesized) key. */
    private void classMember(
            ClassNode cls,
            AstNode pname,
            int propStart,
            List<AbstractObjectProperty> elems,
            boolean isStatic,
            boolean keyConsumed)
            throws IOException {
        int ppos = ts.tokenBeg;
        if (!keyConsumed) {
            consumeToken();
        }
        if (pname instanceof Name || pname instanceof StringLiteral) {
            pname.setLineColumnNumber(lineNumber(), columnNumber());
        }
        String propertyName =
                pname instanceof Name
                        ? ((Name) pname).getIdentifier()
                        : pname instanceof StringLiteral
                                ? ((StringLiteral) pname).getValue()
                                : null;
        int entryKind = METHOD_ENTRY;
        boolean isAsync = false;
        boolean escaped = ts.nameHadEscape;
        if (!(pname instanceof GeneratorMethodDefinition)
                && isFieldEnd(escaped || !(pname instanceof Name) ? null : propertyName)) {
            classField(cls, pname, propStart, elems, isStatic, propertyName);
            return;
        }
        if (peekToken() != Token.LP && pname instanceof Name) {
            if ("get".equals(propertyName)) {
                entryKind = GET_ENTRY;
            } else if ("set".equals(propertyName)) {
                entryKind = SET_ENTRY;
            } else if (isAsyncMethodStart(propertyName, escaped)) {
                isAsync = true;
            } else {
                reportError("msg.bad.prop");
            }
            // the source text of an accessor starts with get/set
            pname = classElementName();
            if (pname == null) {
                reportError("msg.bad.prop");
                return;
            }
            consumeToken();
            propertyName =
                    pname instanceof Name
                            ? ((Name) pname).getIdentifier()
                            : pname instanceof StringLiteral
                                    ? ((StringLiteral) pname).getValue()
                                    : null;
        }
        boolean isGenerator = pname instanceof GeneratorMethodDefinition;
        if (isGenerator) {
            AstNode methodName = ((GeneratorMethodDefinition) pname).getMethodName();
            propertyName =
                    methodName instanceof Name
                            ? ((Name) methodName).getIdentifier()
                            : methodName instanceof StringLiteral
                                    ? ((StringLiteral) methodName).getValue()
                                    : null;
        }
        if (isStatic && "prototype".equals(propertyName)) {
            reportError("msg.class.static.prototype");
        }
        if (!isStatic && "constructor".equals(propertyName)) {
            if (entryKind != METHOD_ENTRY || isGenerator || isAsync) {
                reportError("msg.class.special.ctor");
            }
            if (cls.getConstructor() != null) {
                reportError("msg.class.dup.ctor");
            }
            FunctionNode ctor = function(FunctionNode.FUNCTION_EXPRESSION, true);
            ctor.setMethodDefinition(false);
            ctor.setClassConstructor();
            if (cls.getHeritage() != null) {
                ctor.setDerivedConstructor();
            }
            ctor.setInStrictMode(true);
            cls.setConstructor(ctor);
            return;
        }
        functionIsAsync = isAsync;
        AstNode methodName =
                pname instanceof GeneratorMethodDefinition
                        ? ((GeneratorMethodDefinition) pname).getMethodName()
                        : pname;
        boolean isPrivate = isPrivateName(methodName);
        if (isPrivate) {
            declarePrivateName(
                    cls,
                    (Name) methodName,
                    entryKind == GET_ENTRY
                            ? PRIVATE_GETTER
                            : entryKind == SET_ENTRY ? PRIVATE_SETTER : PRIVATE_OTHER,
                    isStatic);
        }
        AstNode key = pname;
        if (isPrivate) {
            // the key of a private method is its private name, see ClassDefinition
            ClassFieldKey fieldKey =
                    new ClassFieldKey(
                            methodName.getPosition(), privateNameReference(methodName), false);
            fieldKey.setMethod();
            key = new ComputedPropertyKey(methodName.getPosition(), methodName.getLength());
            ((ComputedPropertyKey) key).setExpression(fieldKey);
            if (isStatic) {
                cls.setHasStaticElements();
            }
        }
        ObjectProperty prop = methodDefinition(ppos, propStart, key, entryKind, isGenerator, true);
        ((FunctionNode) prop.getValue()).setInStrictMode(true);
        elems.add(prop);
    }

    private static final int PRIVATE_GETTER = 1, PRIVATE_SETTER = 2, PRIVATE_OTHER = 3;

    /**
     * The private names declared by the classes being parsed, innermost first, and the references
     * to private names in their bodies, resolved at the end of each class (15.7.1 AllPrivate
     * IdentifiersValid).
     */
    private static final class PrivateScope {
        final PrivateScope outer;

        /** The kinds of the names declared (PRIVATE_*), plus 4 when static. */
        final Map<String, Integer> declared = new HashMap<>();

        final List<Name> references = new ArrayList<>();

        PrivateScope(PrivateScope outer) {
            this.outer = outer;
        }
    }

    private PrivateScope privateScope;

    private static boolean isPrivateName(AstNode node) {
        return node instanceof Name && ((Name) node).getIdentifier().startsWith("#");
    }

    /** The name of a class element (peeked): a property name, or a private name. */
    private AstNode classElementName() throws IOException {
        if (peekToken() == Token.PRIVATE_NAME) {
            Name name = new Name(ts.tokenBeg, ts.getString());
            name.setLineColumnNumber(lineNumber(), columnNumber());
            return name;
        }
        if (peekToken() == Token.MUL) {
            // a generator method, whose name may be private
            int pos = ts.tokenBeg;
            consumeToken();
            int lineno = lineNumber();
            int column = columnNumber();
            AstNode name = classElementName();
            AstNode pname = new GeneratorMethodDefinition(pos, ts.tokenEnd - pos, name);
            pname.setLineColumnNumber(lineno, column);
            return pname;
        }
        return objliteralProperty();
    }

    /** Declares a private name of the class (15.7.1 early errors: no duplicates). */
    private void declarePrivateName(ClassNode cls, Name name, int kind, boolean isStatic) {
        String id = name.getIdentifier();
        if ("#constructor".equals(id)) {
            reportError("msg.private.ctor");
        }
        int entry = kind | (isStatic ? 4 : 0);
        Integer previous = privateScope.declared.get(id);
        if (previous == null) {
            privateScope.declared.put(id, entry);
            cls.addPrivateName(id);
            defineSymbol(Token.CONST, id, true);
        } else if ((previous ^ entry) == (PRIVATE_GETTER ^ PRIVATE_SETTER)) {
            // a getter and a setter
            privateScope.declared.put(id, PRIVATE_OTHER | (entry & 4));
        } else {
            reportError("msg.private.dup", id);
        }
    }

    /** A reference to a private name, as the binding of the private name. */
    private Name privateNameReference(AstNode name) {
        Name ref = new Name(name.getPosition(), ((Name) name).getIdentifier());
        ref.setLineColumnNumber(name.getLineno(), name.getColumn());
        return ref;
    }

    /** A reference to the private name just consumed (obj.#x, #x in obj). */
    private Name privateReference() {
        Name name = new Name(ts.tokenBeg, ts.getString());
        name.setLineColumnNumber(lineNumber(), columnNumber());
        if (privateScope != null) {
            privateScope.references.add(name);
        } else if (!isEvalPrivateName(name.getIdentifier())) {
            reportError("msg.private.undeclared", name.getIdentifier(), name.getPosition(), 0);
        }
        return name;
    }

    /** Whether eval code is in a class that declares the private name. */
    private boolean isEvalPrivateName(String name) {
        Predicate<String> names = compilerEnv.getEvalPrivateNames();
        return compilerEnv.isInEval() && names != null && names.test(name);
    }

    private void resolvePrivateReferences(PrivateScope scope) {
        for (Name name : scope.references) {
            if (scope.declared.containsKey(name.getIdentifier())) {
                continue;
            }
            if (scope.outer != null) {
                scope.outer.references.add(name);
            } else if (!isEvalPrivateName(name.getIdentifier())) {
                addError(
                        "msg.private.undeclared",
                        name.getIdentifier(),
                        name.getPosition(),
                        name.getLength());
            }
        }
    }

    /**
     * Whether the class element whose name was just consumed is a field: its name is followed by an
     * initializer, a semicolon, the end of the class or a line terminator (ASI).
     */
    private boolean isFieldEnd(String name) throws IOException {
        int tt = peekToken();
        if (tt == Token.ASSIGN || tt == Token.SEMI || tt == Token.RC) {
            return true;
        }
        if (tt == Token.LP) {
            return false;
        }
        if (("get".equals(name) || "set".equals(name)) && tt == Token.MUL) {
            // get * cannot start an accessor: a line terminator ends the field
            return (peekFlaggedToken() & TI_AFTER_EOL) != 0;
        }
        if ("get".equals(name) || "set".equals(name) || "static".equals(name)) {
            // get, set and static may be followed by a line terminator, async may not
            return false;
        }
        return (peekFlaggedToken() & TI_AFTER_EOL) != 0;
    }

    /** True while the initializer of a class field is parsed, and the arrow functions in it. */
    private boolean inFieldInitializer;

    /**
     * A field of a class (FieldDefinition, 15.7): an entry of the object literal of the members of
     * the class whose key is a {@link ClassFieldKey}, and whose value is the initializer as a
     * method, called with the instance (or the constructor) as this.
     */
    private void classField(
            ClassNode cls,
            AstNode pname,
            int propStart,
            List<AbstractObjectProperty> elems,
            boolean isStatic,
            String propertyName)
            throws IOException {
        if (pname instanceof ComputedPropertyKey) {
            propertyName = null;
        }
        if ("constructor".equals(propertyName) || (isStatic && "prototype".equals(propertyName))) {
            reportError("msg.class.field.name", propertyName);
        }
        AstNode keyExpr;
        if (isPrivateName(pname)) {
            declarePrivateName(cls, (Name) pname, PRIVATE_OTHER, isStatic);
            keyExpr = privateNameReference(pname);
        } else if (pname instanceof ComputedPropertyKey) {
            keyExpr = ((ComputedPropertyKey) pname).getExpression();
        } else if (pname instanceof Name) {
            StringLiteral literal = new StringLiteral(pname.getPosition(), pname.getLength());
            literal.setValue(((Name) pname).getIdentifier());
            literal.setQuoteCharacter('"');
            keyExpr = literal;
        } else {
            keyExpr = pname;
        }
        AstNode value;
        boolean named = false;
        if (matchToken(Token.ASSIGN, true)) {
            FunctionNode initializer = fieldInitializer(ts.tokenEnd);
            AstNode expr =
                    ((ReturnStatement) initializer.getBody().getFirstChild()).getReturnValue();
            named = isAnonymousFunctionDefinition(expr);
            value = initializer;
        } else {
            value = new KeywordLiteral(ts.tokenBeg, 0, Token.NULL);
        }
        // the end of the field: a semicolon, or an inserted one
        if (!matchToken(Token.SEMI, true)
                && peekToken() != Token.RC
                && (peekFlaggedToken() & TI_AFTER_EOL) == 0) {
            reportError("msg.no.semi.stmt");
        }
        ComputedPropertyKey key = new ComputedPropertyKey(pname.getPosition(), pname.getLength());
        key.setExpression(new ClassFieldKey(keyExpr.getPosition(), keyExpr, named));
        ObjectProperty prop = new ObjectProperty(propStart);
        prop.setKeyAndValue(key, value);
        if (value instanceof FunctionNode) {
            prop.setIsNormalMethod();
        }
        elems.add(prop);
        if (isStatic) {
            cls.setHasStaticElements();
        }
    }

    /** True while the statements of a class static block are parsed (not in nested functions). */
    private boolean inStaticBlock;

    /**
     * A class static block (ClassStaticBlock, 15.7): a static element whose initializer is a method
     * with the statements of the block as body, called with the constructor as this.
     */
    private void staticBlock(ClassNode cls, List<AbstractObjectProperty> elems) throws IOException {
        int pos = ts.tokenBeg;
        int lineno = lineNumber(), column = columnNumber();
        FunctionNode fn = new FunctionNode(pos);
        fn.setFunctionType(FunctionNode.FUNCTION_EXPRESSION);
        fn.setMethodDefinition(true);
        fn.setFieldInitializer();
        fn.setInStrictMode(true);
        PerFunctionVariables savedVars = new PerFunctionVariables(fn);
        boolean wasInsideMethod = insideMethod;
        insideMethod = true;
        inStaticBlock = true;
        try {
            AstNode body = parseFunctionBody(FunctionNode.FUNCTION_EXPRESSION, fn);
            fn.setBody(body);
            fn.setRawSourceBounds(pos, ts.tokenEnd);
            fn.setLength(ts.tokenEnd - pos);
        } finally {
            savedVars.restore();
            insideMethod = wasInsideMethod;
        }
        fn.setSourceName(sourceURI);
        fn.setLineColumnNumber(lineno, column);
        fn.setEndLineno(lineNumber());
        KeywordLiteral none = new KeywordLiteral(pos, 0, Token.NULL);
        ClassFieldKey blockKey = new ClassFieldKey(pos, none, false);
        blockKey.setStaticBlock();
        ComputedPropertyKey key = new ComputedPropertyKey(pos, 0);
        key.setExpression(blockKey);
        ObjectProperty prop = new ObjectProperty(pos);
        prop.setKeyAndValue(key, fn);
        prop.setIsNormalMethod();
        elems.add(prop);
        cls.setHasStaticElements();
    }

    /** An initializer: a method whose body returns the AssignmentExpression after the =. */
    private FunctionNode fieldInitializer(int pos) throws IOException {
        int lineno = lineNumber(), column = columnNumber();
        FunctionNode fn = new FunctionNode(pos);
        fn.setFunctionType(FunctionNode.FUNCTION_EXPRESSION);
        fn.setMethodDefinition(true);
        fn.setFieldInitializer();
        fn.setInStrictMode(true);
        PerFunctionVariables savedVars = new PerFunctionVariables(fn);
        boolean wasInsideMethod = insideMethod;
        insideMethod = true;
        ++nestingOfFunction;
        try {
            AstNode value = assignExpr();
            ReturnStatement ret =
                    new ReturnStatement(value.getPosition(), value.getLength(), value);
            ret.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
            ret.setLineColumnNumber(value.getLineno(), value.getColumn());
            Block body = new Block(value.getPosition());
            body.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
            body.setLineColumnNumber(value.getLineno(), value.getColumn());
            body.addStatement(ret);
            body.setLength(value.getLength());
            fn.setBody(body);
            int end = pos + body.getPosition() + body.getLength();
            fn.setRawSourceBounds(pos, end);
            fn.setLength(end - pos);
        } finally {
            --nestingOfFunction;
            savedVars.restore();
            insideMethod = wasInsideMethod;
        }
        fn.setSourceName(sourceURI);
        fn.setLineColumnNumber(lineno, column);
        fn.setEndLineno(lineNumber());
        return fn;
    }

    /** IsAnonymousFunctionDefinition (8.4.3). */
    private static boolean isAnonymousFunctionDefinition(AstNode expr) {
        while (expr instanceof ParenthesizedExpression) {
            expr = ((ParenthesizedExpression) expr).getExpression();
        }
        if (expr instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) expr;
            return fn.getFunctionName() == null || fn.getFunctionName().length() == 0;
        }
        if (expr instanceof ClassNode) {
            return ((ClassNode) expr).getClassName() == null;
        }
        return false;
    }

    /**
     * Whether the property name just consumed is the async of an async method: an unescaped async,
     * then the method name on the same line.
     */
    private boolean isAsyncMethodStart(String propertyName, boolean escaped) throws IOException {
        return "async".equals(propertyName)
                && !escaped
                && compilerEnv.isLatestEcmaScript()
                && (peekFlaggedToken() & TI_AFTER_EOL) == 0;
    }

    /** Set by methodDefinition for function(): the method being parsed is a generator. */
    private boolean methodIsGenerator;

    /** How many Token#ITER_OPEN have been created, see IRFactory#guardIterators. */
    int iteratorOpenCount;

    /** The scope of the destructuring being desugared, and its LET, which hold its temporaries. */
    private Scope destructuringScope;

    private Node destructuringLet;

    private String defineTemp() {
        String name = currentScriptOrFn.getNextTempName();
        pushScope(destructuringScope);
        try {
            defineSymbol(Token.LET, name, true);
        } finally {
            popScope();
        }
        // written before it is read: no initializer
        destructuringLet.addChildToBack(createName(Token.NAME, name, null));
        return name;
    }

    private void processDestructuringDefaults(
            int variableType,
            Node parent,
            List<String> destructuringNames,
            Assignment n,
            Node rightElem,
            int setOp,
            Transformer transformer,
            boolean isFunctionParameter) {
        Node left = n.getLeft();
        Node right = null;
        if (left.getType() == Token.NAME) {
            String name = left.getString();
            // x = (x == undefined) ?
            //          (($1[0] == undefined) ?
            //              1
            //              : $1[0])
            //          : x

            right = (transformer != null) ? transformer.transform(n.getRight()) : n.getRight();
            if (transformer != null) {
                nameAnonymousFunction(right, name);
            }

            Node cond_inner =
                    new Node(
                            Token.HOOK,
                            new Node(
                                    Token.SHEQ,
                                    new KeywordLiteral().setType(Token.UNDEFINED),
                                    rightElem),
                            right,
                            rightElem);

            // the default applies when the element is undefined; before VERSION_ECMASCRIPT, only
            // when the target is undefined too
            Node cond =
                    compilerEnv.isLatestEcmaScript()
                            ? cond_inner
                            : new Node(
                                    Token.HOOK,
                                    new Node(
                                            Token.SHEQ,
                                            new KeywordLiteral().setType(Token.UNDEFINED),
                                            createName(name)),
                                    cond_inner,
                                    left);

            // store it to be transformed later
            if (transformer == null) {
                currentScriptOrFn.putDestructuringRvalues(cond_inner, right, name);
            }

            parent.addChildToBack(destructuringSet(setOp, name, cond, variableType));
            if (variableType != -1) {
                defineSymbol(variableType, name, true);
                destructuringNames.add(name);
            }
        } else {
            // Handle nested destructuring patterns with defaults, eg: [[x, y, z] = [4, 5, 6]]
            if (left instanceof ArrayLiteral || left instanceof ObjectLiteral) {
                right = (transformer != null) ? transformer.transform(n.getRight()) : n.getRight();

                Node cond_default =
                        new Node(
                                Token.HOOK,
                                new Node(
                                        Token.SHEQ,
                                        new KeywordLiteral().setType(Token.UNDEFINED),
                                        rightElem),
                                right,
                                rightElem);

                if (transformer == null) {
                    currentScriptOrFn.putDestructuringRvalues(cond_default, right);
                }

                parent.addChildToBack(
                        nestedDestructuring(
                                destructuringNames,
                                variableType,
                                left,
                                cond_default,
                                currentScriptOrFn.getNextTempName(),
                                null,
                                transformer,
                                isFunctionParameter));
            } else if (isMemberTarget(left)
                    && transformer != null
                    && compilerEnv.isLatestEcmaScript()) {
                // {a: o.p = 1}: the reference is evaluated before the property is read
                parent.addChildToBack(
                        simpleAssignment(
                                left,
                                defaultedValue(rightElem, n.getRight(), transformer),
                                transformer));
            } else {
                reportError("msg.bad.assign.left");
            }
        }
    }

    /**
     * NamedEvaluation of a binding's default value: an anonymous function (function, generator or
     * arrow expression, possibly parenthesized) is named after the binding. Called once the
     * function is transformed, so that the name does not become a binding inside it.
     */
    void nameAnonymousFunction(Node value, String name) {
        if (value != null && value.getType() == Token.CLASS) {
            nameAnonymousClass(value, name);
            return;
        }
        if (value == null
                || value.getType() != Token.FUNCTION
                || !compilerEnv.isLatestEcmaScript()) {
            return;
        }
        FunctionNode fn =
                currentScriptOrFn.getFunctionNode(value.getExistingIntProp(Node.FUNCTION_PROP));
        if (fn.getFunctionName() == null) {
            fn.setFunctionName(new Name(fn.getPosition(), name));
        }
    }

    /**
     * NamedEvaluation of an anonymous class (a CLASS node whose string has no name, see
     * IRFactory#transformClass): the class and its constructor, if any, are named.
     */
    void nameAnonymousClass(Node value, String name) {
        if (value.getString().length() != 1) {
            return;
        }
        value.setString(value.getString() + name);
        Node ctor = value.getFirstChild();
        if (ctor.getType() == Token.FUNCTION) {
            FunctionNode fn =
                    currentScriptOrFn.getFunctionNode(ctor.getExistingIntProp(Node.FUNCTION_PROP));
            fn.setFunctionName(new Name(fn.getPosition(), name));
        }
    }

    static Object getPropKey(Node id) {
        Object key;
        if (id instanceof Name) {
            String s = ((Name) id).getIdentifier();
            key = ScriptRuntime.getIndexObject(s);
        } else if (id instanceof StringLiteral) {
            String s = ((StringLiteral) id).getValue();
            key = ScriptRuntime.getIndexObject(s);
        } else if (id instanceof NumberLiteral) {
            double n = ((NumberLiteral) id).getNumber();
            key = ScriptRuntime.getIndexObject(n);
        } else if (id instanceof BigIntLiteral) {
            key = ScriptRuntime.getIndexObject(((BigIntLiteral) id).getBigInt().toString());
        } else if (id instanceof GeneratorMethodDefinition) {
            key = getPropKey(((GeneratorMethodDefinition) id).getMethodName());
        } else {
            key = null; // Filled later
        }
        return key;
    }

    private void setupDefaultValues(
            String tempName,
            Node parent,
            AstNode defaultValue,
            int setOp,
            Transformer transformer) {
        if (defaultValue != null) {
            // if there's defaultValue it can be substituted for tempName if that's undefined
            // i.e. $1 = ($1 == undefined) ? defaultValue : $1

            Node defaultRvalue =
                    transformer != null ? transformer.transform(defaultValue) : defaultValue;

            Node undefined = new KeywordLiteral().setType(Token.UNDEFINED);

            Node cond_default =
                    new Node(
                            Token.HOOK,
                            new Node(Token.SHEQ, createName(tempName), undefined),
                            defaultRvalue,
                            createName(tempName));

            if (transformer == null) {
                currentScriptOrFn.putDestructuringRvalues(cond_default, defaultRvalue);
            }

            Node set_default =
                    new Node(setOp, createName(Token.BINDNAME, tempName, null), cond_default);
            parent.addChildToBack(set_default);
        }
    }

    boolean destructuringObject(
            ObjectLiteral node,
            int variableType,
            String tempName,
            Node parent,
            List<String> destructuringNames,
            AstNode defaultValue, /* defaultValue to use in function param decls */
            Transformer transformer,
            boolean isFunctionParameter,
            Node letNode,
            Scope letExprScope) {
        boolean empty = true;
        int setOp = variableType == Token.CONST ? Token.SETCONST : Token.SETNAME;
        boolean defaultValuesSetup = false;
        List<Object> extractedKeys = new ArrayList<>(); // store strings or nodes for computed keys

        if (compilerEnv.isLatestEcmaScript()) {
            // RequireObjectCoercible, after the default value of a parameter
            if (defaultValue != null) {
                setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                defaultValuesSetup = true;
            }
            parent.addChildToBack(new Node(Token.REQUIRE_OBJECT, createName(tempName)));
            empty = false;
        }

        // Track position to validate rest element is last
        List<AbstractObjectProperty> elements = node.getElements();
        int elementIndex = 0;

        for (AbstractObjectProperty abstractProp : elements) {
            if (abstractProp instanceof SpreadObjectProperty) {
                // Validate that rest element is in last position
                if (elementIndex < elements.size() - 1) {
                    reportError("msg.rest.must.be.last");
                    return false;
                }
                // Handle object rest properties: {a, b, ...rest}
                SpreadObjectProperty spreadProp = (SpreadObjectProperty) abstractProp;
                Spread spread = spreadProp.getSpreadNode();
                AstNode restTarget = spread.getExpression();

                // Setup default value if needed (must happen before OBJECT_REST uses tempName)
                if (defaultValue != null && !defaultValuesSetup) {
                    setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                    defaultValuesSetup = true;
                }

                // copy source excluding extracted keys
                Node restOp = new Node(Token.OBJECT_REST);
                restOp.putProp(
                        Node.OBJECT_IDS_PROP,
                        extractedKeys.toArray(
                                new Object[0])); // store excluded keys (Strings or Nodes)
                restOp.addChildToBack(createName(tempName)); // source

                if (restTarget.getType() == Token.NAME) {
                    // Simple name: {...rest}
                    String restName = ((Name) restTarget).getIdentifier();

                    parent.addChildToBack(destructuringSet(setOp, restName, restOp, variableType));

                    if (variableType != -1) {
                        defineSymbol(variableType, restName, true);
                        destructuringNames.add(restName);
                    }
                } else {
                    // Pattern: ...{x, y} or ...[length]
                    // Collect rest object into temporary, then destructure
                    parent.addChildToBack(
                            nestedDestructuring(
                                    destructuringNames,
                                    variableType,
                                    restTarget,
                                    restOp,
                                    currentScriptOrFn.getNextTempName(),
                                    null,
                                    transformer,
                                    isFunctionParameter));
                }
                break; // rest must be the last argument
            }

            ObjectProperty prop = (ObjectProperty) abstractProp;

            int lineno = 0, column = 0;
            // This function is sometimes called from the IRFactory
            // when executing regression tests, and in those cases the
            // tokenStream isn't set.  Deal with it.
            if (ts != null) {
                lineno = lineNumber();
                column = columnNumber();
            }
            AstNode id = prop.getKey();

            Node rightElem = null;
            String keyName = null;
            if (id instanceof Name) {
                keyName = ((Name) id).getIdentifier();
                Node s = Node.newString(keyName);
                rightElem = new Node(Token.GETPROP, createName(tempName), s);
                extractedKeys.add(keyName);
            } else if (id instanceof StringLiteral) {
                keyName = ((StringLiteral) id).getValue();
                Node s = Node.newString(keyName);
                rightElem = new Node(Token.GETPROP, createName(tempName), s);
                extractedKeys.add(keyName);
            } else if (id instanceof NumberLiteral && !compilerEnv.isLatestEcmaScript()) {
                Node s = createNumber((int) ((NumberLiteral) id).getNumber());
                rightElem = new Node(Token.GETELEM, createName(tempName), s);
            } else if (id instanceof BigIntLiteral && !compilerEnv.isLatestEcmaScript()) {
                Node s = Node.newString(((BigIntLiteral) id).getBigInt().toString());
                rightElem = new Node(Token.GETELEM, createName(tempName), s);
            } else if (id instanceof NumberLiteral) {
                // the key is the number's string ({1.5: x} reads "1.5")
                String key = ScriptRuntime.toString(((NumberLiteral) id).getNumber());
                Node s = Node.newString(key);
                rightElem = new Node(Token.GETELEM, createName(tempName), s);
                extractedKeys.add(key);
            } else if (id instanceof BigIntLiteral) {
                String key = ((BigIntLiteral) id).getBigInt().toString();
                Node s = Node.newString(key);
                rightElem = new Node(Token.GETELEM, createName(tempName), s);
                extractedKeys.add(key);
            } else if (id instanceof ComputedPropertyKey) {
                // handle computed property key: [...key]
                // Create a temp variable for the computed key within the LETEXPR scope
                // to ensure the expression is only evaluated once
                AstNode computedExpr = ((ComputedPropertyKey) id).getExpression();
                String keyTempName;
                if (compilerEnv.isLatestEcmaScript()) {
                    // evaluated in order, right before the property is read
                    keyTempName = defineTemp();
                    Node keyExpr =
                            transformer != null
                                    ? transformer.transform(computedExpr)
                                    : computedExpr;
                    if (transformer != null) {
                        // ToPropertyKey before the target of the property is evaluated
                        keyExpr = new Node(Token.FIELD_KEY, keyExpr);
                        keyExpr.putIntProp(Node.AWAIT_KIND_PROP, 5);
                    }
                    Node setKey =
                            new Node(
                                    Token.SETNAME,
                                    createName(Token.BINDNAME, keyTempName, null),
                                    keyExpr);
                    if (transformer == null) {
                        currentScriptOrFn.putDestructuringRvalues(setKey, computedExpr);
                    }
                    parent.addChildToBack(setKey);
                } else {
                    // before VERSION_ECMASCRIPT, every key is evaluated first, in the let
                    keyTempName = currentScriptOrFn.getNextTempName();
                    Node tempVar;
                    if (transformer != null) {
                        tempVar =
                                createName(
                                        Token.NAME,
                                        keyTempName,
                                        transformer.transform(computedExpr));
                    } else {
                        tempVar = createName(Token.NAME, keyTempName, computedExpr);
                        currentScriptOrFn.putDestructuringRvalues(tempVar, computedExpr);
                    }
                    letNode.addChildToBack(tempVar);
                    pushScope(letExprScope);
                    try {
                        defineSymbol(Token.LET, keyTempName, true);
                    } finally {
                        popScope();
                    }
                }

                Node keyRef = createName(keyTempName);
                extractedKeys.add(keyRef);
                rightElem = new Node(Token.GETELEM, createName(tempName), keyRef);
            } else {
                throw codeBug();
            }

            rightElem.setLineColumnNumber(lineno, column);
            if (defaultValue != null && !defaultValuesSetup) {
                setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                defaultValuesSetup = true;
            }

            AstNode value = prop.getValue();
            if (value.getType() == Token.NAME) {
                String name = ((Name) value).getIdentifier();
                parent.addChildToBack(destructuringSet(setOp, name, rightElem, variableType));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
            } else if (isEvaluatedMemberTarget(value, transformer)) {
                // KeyedDestructuringAssignmentEvaluation: the reference of the target (its object
                // and key expression) is evaluated before the property of the source is read
                AstNode targetObject =
                        value instanceof PropertyGet
                                ? ((PropertyGet) value).getTarget()
                                : ((ElementGet) value).getTarget();
                String objectTemp = defineTemp();
                parent.addChildToBack(
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, objectTemp, null),
                                transformer.transform(targetObject)));
                Node set;
                if (value instanceof PropertyGet
                        && isPrivateName(((PropertyGet) value).getProperty())) {
                    // a private field: assigned through the object in the temp
                    PropertyGet member = new PropertyGet(value.getPosition(), value.getLength());
                    member.setTarget(new Name(value.getPosition(), objectTemp));
                    member.setProperty(((PropertyGet) value).getProperty());
                    set = simpleAssignment(member, rightElem, transformer);
                } else if (value instanceof PropertyGet) {
                    set =
                            new Node(
                                    Token.SETPROP,
                                    createName(objectTemp),
                                    Node.newString(
                                            ((PropertyGet) value).getProperty().getIdentifier()),
                                    rightElem);
                } else {
                    String keyTemp = defineTemp();
                    parent.addChildToBack(
                            new Node(
                                    Token.SETNAME,
                                    createName(Token.BINDNAME, keyTemp, null),
                                    transformer.transform(((ElementGet) value).getElement())));
                    set =
                            new Node(
                                    Token.SETELEM,
                                    createName(objectTemp),
                                    createName(keyTemp),
                                    rightElem);
                }
                parent.addChildToBack(set);
            } else if (value.getType() == Token.ASSIGN) {
                processDestructuringDefaults(
                        variableType,
                        parent,
                        destructuringNames,
                        (Assignment) value,
                        rightElem,
                        setOp,
                        transformer,
                        isFunctionParameter);
            } else {
                parent.addChildToBack(
                        nestedDestructuring(
                                destructuringNames,
                                variableType,
                                value,
                                rightElem,
                                currentScriptOrFn.getNextTempName(),
                                null,
                                transformer,
                                isFunctionParameter));
            }
            empty = false;
            elementIndex++;
        }
        return empty;
    }

    /** A property or element target of an object pattern whose reference is evaluated first. */
    private boolean isEvaluatedMemberTarget(AstNode value, Transformer transformer) {
        if (transformer == null || !compilerEnv.isLatestEcmaScript()) {
            return false;
        }
        AstNode target;
        if (value instanceof PropertyGet) {
            target = ((PropertyGet) value).getTarget();
        } else if (value instanceof ElementGet) {
            target = ((ElementGet) value).getTarget();
        } else {
            return false;
        }
        return !(target instanceof KeywordLiteral) || target.getType() != Token.SUPER;
    }

    protected Node createName(String name) {
        checkActivationName(name, Token.NAME);
        return Node.newString(Token.NAME, name);
    }

    protected Node createName(int type, String name, Node child) {
        Node result = createName(name);
        result.setType(type);
        if (child != null) result.addChildToBack(child);
        return result;
    }

    protected Node createNumber(double number) {
        return Node.newNumber(number);
    }

    /**
     * Create a node that can be used to hold lexically scoped variable definitions (via let
     * declarations).
     *
     * @param token the token of the node to create
     * @param lineno line number of source
     * @return the created node
     */
    protected Scope createScopeNode(int token, int lineno, int column) {
        Scope scope = new Scope();
        scope.setType(token);
        scope.setLineColumnNumber(lineno, column);
        return scope;
    }

    // Quickie tutorial for some of the interpreter bytecodes.
    //
    // GETPROP - for normal foo.bar prop access; right side is a name
    // GETELEM - for normal foo[bar] element access; rhs is an expr
    // SETPROP - for assignment when left side is a GETPROP
    // SETELEM - for assignment when left side is a GETELEM
    // DELPROP - used for delete foo.bar or foo[bar]
    //
    // GET_REF, SET_REF, DEL_REF - in general, these mean you're using
    // get/set/delete on a right-hand side expression (possibly with no
    // explicit left-hand side) that doesn't use the normal JavaScript
    // Object (i.e. ScriptableObject) get/set/delete functions, but wants
    // to provide its own versions instead.  It will ultimately implement
    // Ref, and currently SpecialRef (for __proto__ etc.) and XmlName
    // (for E4X XML objects) are the only implementations.  The runtime
    // notices these bytecodes and delegates get/set/delete to the object.
    //
    // BINDNAME:  used in assignments.  LHS is evaluated first to get a
    // specific object containing the property ("binding" the property
    // to the object) so that it's always the same object, regardless of
    // side effects in the RHS.
    protected Node simpleAssignment(Node left, Node right) {
        return simpleAssignment(left, right, null);
    }

    protected Node simpleAssignment(Node left, Node right, Transformer transformer) {
        int nodeType = left.getType();
        switch (nodeType) {
            case Token.UNDEFINED:
                left = Node.newString(Token.BINDNAME, "undefined");
                return new Node(Token.SETNAME, left, right);

            case Token.NAME:
                String name = ((Name) left).getIdentifier();
                if (inUseStrictDirective && ("eval".equals(name) || "arguments".equals(name))) {
                    reportError("msg.bad.id.strict", name);
                }
                left.setType(Token.BINDNAME);
                return new Node(Token.SETNAME, left, right);

            case Token.GETPROP:
            case Token.GETELEM:
                {
                    Node obj, id;
                    // If it's a PropertyGet or ElementGet, we're in the parse pass.
                    // We could alternately have PropertyGet and ElementGet
                    // override getFirstChild/getLastChild and return the appropriate
                    // field, but that seems just as ugly as this casting.
                    if (left instanceof PropertyGet) {
                        AstNode target = ((PropertyGet) left).getTarget();
                        obj = transformer != null ? transformer.transform(target) : target;
                        id = ((PropertyGet) left).getProperty();
                    } else if (left instanceof ElementGet) {
                        AstNode target = ((ElementGet) left).getTarget();
                        AstNode elem = ((ElementGet) left).getElement();
                        obj = transformer != null ? transformer.transform(target) : target;
                        id = transformer != null ? transformer.transform(elem) : elem;
                    } else {
                        // This branch is called during IRFactory transform pass.
                        obj = left.getFirstChild();
                        id = left.getLastChild();
                    }
                    int type;
                    if (nodeType == Token.GETPROP) {
                        type = Token.SETPROP;
                        // TODO(stevey) - see https://bugzilla.mozilla.org/show_bug.cgi?id=492036
                        // The new AST code generates NAME tokens for GETPROP ids where the old
                        // parser
                        // generated STRING nodes. If we don't set the type to STRING below, this
                        // will
                        // cause java.lang.VerifyError in codegen for code like
                        // "var obj={p:3};[obj.p]=[9];"
                        id.setType(Token.STRING);
                    } else {
                        type = Token.SETELEM;
                    }
                    return new Node(type, obj, id, right);
                }
            case Token.GET_REF:
                {
                    Node ref = left.getFirstChild();
                    checkMutableReference(ref);
                    return new Node(Token.SET_REF, ref, right);
                }
        }

        throw codeBug();
    }

    protected void checkMutableReference(Node n) {
        int memberTypeFlags = n.getIntProp(Node.MEMBER_TYPE_PROP, 0);
        if ((memberTypeFlags & Node.DESCENDANTS_FLAG) != 0) {
            reportError("msg.bad.assign.left");
        }
    }

    // remove any ParenthesizedExpression wrappers
    protected AstNode removeParens(AstNode node) {
        while (node instanceof ParenthesizedExpression) {
            node = ((ParenthesizedExpression) node).getExpression();
        }
        return node;
    }

    void markDestructuring(AstNode node) {
        if (node instanceof DestructuringForm) {
            ((DestructuringForm) node).setIsDestructuring(true);
            // rest element should be positioned in the last
            if (node instanceof ArrayLiteral) {
                ArrayLiteral array = (ArrayLiteral) node;
                List<AstNode> elements = array.getElements();
                for (int i = 0; i < elements.size(); i++) {
                    AstNode e = elements.get(i);
                    if (e instanceof Spread && i < elements.size() - 1) {
                        reportError("msg.parm.after.rest");
                    }
                }
                // check for trailing comma after rest: [...x,]
                // destructuringLength should be > elements.size()
                if (!elements.isEmpty()
                        && elements.get(elements.size() - 1) instanceof Spread
                        && array.getDestructuringLength() > elements.size()) {
                    reportError("msg.parm.after.rest");
                }
            }
        } else if (node instanceof ParenthesizedExpression) {
            markDestructuring(((ParenthesizedExpression) node).getExpression());
        }
    }

    // throw a failed-assertion with some helpful debugging info
    private RuntimeException codeBug() throws RuntimeException {
        throw Kit.codeBug(
                "ts.cursor="
                        + ts.cursor
                        + ", ts.tokenBeg="
                        + ts.tokenBeg
                        + ", currentToken="
                        + currentToken);
    }

    public boolean inUseStrictDirective() {
        return inUseStrictDirective;
    }

    public void reportErrorsIfExists(int baseLineno) {
        if (this.syntaxErrorCount != 0) {
            String msg = String.valueOf(this.syntaxErrorCount);
            msg = lookupMessage("msg.got.syntax.errors", msg);
            if (!compilerEnv.isIdeMode())
                throw errorReporter.runtimeError(msg, sourceURI, baseLineno, null, 0);
        }
    }

    public void setSourceURI(String sourceURI) {
        this.sourceURI = sourceURI;
    }

    public interface CurrentPositionReporter {
        public int getPosition();

        public int getLength();

        public int getLineno();

        public String getLine();

        public int getOffset();
    }
}
