/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/** Additional interpreter-specific codes */
abstract class Icode {

    static final int

            // delete operator used on a name
            DELNAME = 0,

            // Stack: ... value1 -> ... value1 value1
            DUP = DELNAME - 1,

            // Stack: ... value2 value1 -> ... value2 value1 value2 value1
            DUP2 = DUP - 1,

            // Stack: ... value2 value1 -> ... value1 value2
            SWAP = DUP2 - 1,

            // Stack: ... value1 -> ...
            POP = SWAP - 1,

            // Store stack top into return register and then pop it
            POP_RESULT = POP - 1,

            // To jump conditionally and pop additional stack value
            IFEQ_POP = POP_RESULT - 1,

            // various types of ++/--
            VAR_INC_DEC = IFEQ_POP - 1,
            NAME_INC_DEC = VAR_INC_DEC - 1,
            PROP_INC_DEC = NAME_INC_DEC - 1,
            ELEM_INC_DEC = PROP_INC_DEC - 1,
            REF_INC_DEC = ELEM_INC_DEC - 1,

            // load/save scope from/to local
            SCOPE_LOAD = REF_INC_DEC - 1,
            SCOPE_SAVE = SCOPE_LOAD - 1,
            TYPEOFNAME = SCOPE_SAVE - 1,

            // helper for function calls
            NAME_AND_THIS = TYPEOFNAME - 1,
            PROP_AND_THIS = NAME_AND_THIS - 1,
            ELEM_AND_THIS = PROP_AND_THIS - 1,
            VALUE_AND_THIS = ELEM_AND_THIS - 1,
            NAME_AND_THIS_OPTIONAL = VALUE_AND_THIS - 1,
            PROP_AND_THIS_OPTIONAL = NAME_AND_THIS_OPTIONAL - 1,
            ELEM_AND_THIS_OPTIONAL = PROP_AND_THIS_OPTIONAL - 1,
            VALUE_AND_THIS_OPTIONAL = ELEM_AND_THIS_OPTIONAL - 1,

            // Create closure object for nested functions
            CLOSURE_EXPR = VALUE_AND_THIS_OPTIONAL - 1,
            CLOSURE_STMT = CLOSURE_EXPR - 1,

            // Special calls
            CALLSPECIAL = CLOSURE_STMT - 1,
            CALLSPECIAL_OPTIONAL = CALLSPECIAL - 1,

            // To return undefined value
            RETUNDEF = CALLSPECIAL_OPTIONAL - 1,

            // Exception handling implementation
            GOSUB = RETUNDEF - 1,
            STARTSUB = GOSUB - 1,
            RETSUB = STARTSUB - 1,

            // To indicating a line number change in icodes.
            LINE = RETSUB - 1,

            // To store shorts and ints inline
            SHORTNUMBER = LINE - 1,
            INTNUMBER = SHORTNUMBER - 1,

            // To create and populate array to hold values for [] and {} literals
            LITERAL_NEW_OBJECT = INTNUMBER - 1,
            LITERAL_NEW_ARRAY = LITERAL_NEW_OBJECT - 1,
            LITERAL_SET = LITERAL_NEW_ARRAY - 1,
            METHOD_EXPR = LITERAL_SET - 1,

            // Array literal with skipped index like [1,,2]
            SPARE_ARRAYLIT = METHOD_EXPR - 1,

            // Load index register to prepare for the following index operation
            REG_IND_C0 = SPARE_ARRAYLIT - 1,
            REG_IND_C1 = REG_IND_C0 - 1,
            REG_IND_C2 = REG_IND_C1 - 1,
            REG_IND_C3 = REG_IND_C2 - 1,
            REG_IND_C4 = REG_IND_C3 - 1,
            REG_IND_C5 = REG_IND_C4 - 1,
            REG_IND1 = REG_IND_C5 - 1,
            REG_IND2 = REG_IND1 - 1,
            REG_IND4 = REG_IND2 - 1,

            // Load string register to prepare for the following string operation
            REG_STR_C0 = REG_IND4 - 1,
            REG_STR_C1 = REG_STR_C0 - 1,
            REG_STR_C2 = REG_STR_C1 - 1,
            REG_STR_C3 = REG_STR_C2 - 1,
            REG_STR1 = REG_STR_C3 - 1,
            REG_STR2 = REG_STR1 - 1,
            REG_STR4 = REG_STR2 - 1,

            // Version of getvar/setvar that read var index directly from bytecode
            GETVAR1 = REG_STR4 - 1,
            SETVAR1 = GETVAR1 - 1,

            // Load undefined
            UNDEF = SETVAR1 - 1,
            ZERO = UNDEF - 1,
            ONE = ZERO - 1,

            // entrance and exit from .()
            ENTERDQ = ONE - 1,
            LEAVEDQ = ENTERDQ - 1,
            TAIL_CALL = LEAVEDQ - 1,

            // Clear local to allow GC its context
            LOCAL_CLEAR = TAIL_CALL - 1,

            // Literal get/set
            LITERAL_GETTER = LOCAL_CLEAR - 1,
            LITERAL_SETTER = LITERAL_GETTER - 1,

            // const
            SETCONST = LITERAL_SETTER - 1,
            SETCONSTVAR = SETCONST - 1,
            SETCONSTVAR1 = SETCONSTVAR - 1,

            // Generator opcodes (along with Token.YIELD)
            GENERATOR = SETCONSTVAR1 - 1,
            GENERATOR_END = GENERATOR - 1,
            DEBUGGER = GENERATOR_END - 1,
            GENERATOR_RETURN = DEBUGGER - 1,
            YIELD_STAR = GENERATOR_RETURN - 1,

            // Load BigInt register to prepare for the following BigInt operation
            REG_BIGINT_C0 = YIELD_STAR - 1,
            REG_BIGINT_C1 = REG_BIGINT_C0 - 1,
            REG_BIGINT_C2 = REG_BIGINT_C1 - 1,
            REG_BIGINT_C3 = REG_BIGINT_C2 - 1,
            REG_BIGINT1 = REG_BIGINT_C3 - 1,
            REG_BIGINT2 = REG_BIGINT1 - 1,
            REG_BIGINT4 = REG_BIGINT2 - 1,

            // Call to GetTemplateLiteralCallSite
            TEMPLATE_LITERAL_CALLSITE = REG_BIGINT4 - 1,
            LITERAL_KEY_SET = TEMPLATE_LITERAL_CALLSITE - 1,

            // Jump if stack head is null or undefined
            IF_NULL_UNDEF = LITERAL_KEY_SET - 1,
            IF_NOT_NULL_UNDEF = IF_NULL_UNDEF - 1,

            // Call a method on the super object, i.e. super.foo()
            CALL_ON_SUPER = IF_NOT_NULL_UNDEF - 1,

            // delete super.prop
            DELPROP_SUPER = CALL_ON_SUPER - 1,

            // spread
            SPREAD = DELPROP_SUPER - 1,

            // object rest - create object excluding extracted keys
            OBJECT_REST = SPREAD - 1,

            // Per-iteration copy of a loop's let scope
            COPY_SCOPE = OBJECT_REST - 1,

            // Temporal dead zone of let/const bindings
            TDZ_SENTINEL = COPY_SCOPE - 1,
            CHECK_TDZ = TDZ_SENTINEL - 1,

            // Global let/const declarations of a script
            GLOBAL_LEXICALS = CHECK_TDZ - 1,

            // The iterator protocol of array destructuring
            ITER_OPEN = GLOBAL_LEXICALS - 1,
            ITER_STEP = ITER_OPEN - 1,
            ITER_REST = ITER_STEP - 1,
            ITER_CLOSE = ITER_REST - 1,
            ITER_OPEN_GUARDED = ITER_CLOSE - 1,
            ITER_GUARD_INIT = ITER_OPEN_GUARDED - 1,
            ITER_GUARD_CLOSE = ITER_GUARD_INIT - 1,

            // RequireObjectCoercible of an object pattern
            REQUIRE_OBJECT = ITER_GUARD_CLOSE - 1,

            // Calls with spread arguments: their only argument is the array of the arguments
            CALL_SPREAD = REQUIRE_OBJECT - 1,
            CALL_SPREAD_ON_SUPER = CALL_SPREAD - 1,
            NEW_SPREAD = CALL_SPREAD_ON_SUPER - 1,

            // IteratorClose of a for-of loop
            ENUM_CLOSE = NEW_SPREAD - 1,
            ENUM_CLOSE_ABRUPT = ENUM_CLOSE - 1,

            // Class definition: constructor, heritage, prototype and static members
            CLASS_DEFINE = ENUM_CLOSE_ABRUPT - 1,
            // this of a derived constructor, and the super constructor called by super(...)
            DERIVED_THIS = CLASS_DEFINE - 1,
            SUPER_CALLEE = DERIVED_THIS - 1,
            // see Token#FN_NAME_ASSIGN
            FN_NAME_ASSIGN = SUPER_CALLEE - 1,
            // ToPropertyKey of an object key, before the get and the set of a compound assignment
            ELEM_KEY = FN_NAME_ASSIGN - 1,
            // the value of a __proto__: v entry of an object literal
            LITERAL_PROTO = ELEM_KEY - 1,
            // the operand of an await in an async generator, as yielded (Token#AWAIT)
            AWAIT_REQUEST = LITERAL_PROTO - 1,
            // the other kinds of Token#AWAIT (Node#AWAIT_KIND_PROP)
            ASYNC_DELEGATE = AWAIT_REQUEST - 1,
            ASYNC_ITER_OPEN = ASYNC_DELEGATE - 1,
            ASYNC_ITER_NEXT = ASYNC_ITER_OPEN - 1,
            ASYNC_ITER_CLOSE = ASYNC_ITER_NEXT - 1,
            ASYNC_ITER_CLOSE_ABRUPT = ASYNC_ITER_CLOSE - 1,
            // import(): the referrer name, the specifier and the options (Token#IMPORT_CALL)
            IMPORT_CALL = ASYNC_ITER_CLOSE_ABRUPT - 1,
            // class fields (Token#FIELD_KEY, Token#CLASS_STATIC)
            FIELD_KEY = IMPORT_CALL - 1,
            FIELD_KEY_NAMED = FIELD_KEY - 1,
            CLASS_STATIC = FIELD_KEY_NAMED - 1,

            // Last icode
            MIN_ICODE = CLASS_STATIC;

    /** The icode of a Token#AWAIT of a kind (Node#AWAIT_KIND_PROP), in the order of the kinds. */
    static final int[] AWAIT_ICODES = {
        AWAIT_REQUEST,
        ASYNC_DELEGATE,
        ASYNC_ITER_OPEN,
        ASYNC_ITER_NEXT,
        ASYNC_ITER_CLOSE,
        ASYNC_ITER_CLOSE_ABRUPT
    };

    static String bytecodeName(int bytecode) {
        if (!validBytecode(bytecode)) {
            throw new IllegalArgumentException(String.valueOf(bytecode));
        }

        if (!Token.printICode) {
            return String.valueOf(bytecode);
        }

        if (validTokenCode(bytecode)) {
            return Token.name(bytecode);
        }

        switch (bytecode) {
            case DELNAME:
                return "DELNAME";
            case DUP:
                return "DUP";
            case DUP2:
                return "DUP2";
            case SWAP:
                return "SWAP";
            case POP:
                return "POP";
            case POP_RESULT:
                return "POP_RESULT";
            case IFEQ_POP:
                return "IFEQ_POP";
            case VAR_INC_DEC:
                return "VAR_INC_DEC";
            case NAME_INC_DEC:
                return "NAME_INC_DEC";
            case PROP_INC_DEC:
                return "PROP_INC_DEC";
            case ELEM_INC_DEC:
                return "ELEM_INC_DEC";
            case REF_INC_DEC:
                return "REF_INC_DEC";
            case SCOPE_LOAD:
                return "SCOPE_LOAD";
            case SCOPE_SAVE:
                return "SCOPE_SAVE";
            case TYPEOFNAME:
                return "TYPEOFNAME";
            case NAME_AND_THIS:
                return "NAME_AND_THIS";
            case PROP_AND_THIS:
                return "PROP_AND_THIS";
            case ELEM_AND_THIS:
                return "ELEM_AND_THIS";
            case VALUE_AND_THIS:
                return "VALUE_AND_THIS";
            case NAME_AND_THIS_OPTIONAL:
                return "NAME_AND_THIS_OPTIONAL";
            case PROP_AND_THIS_OPTIONAL:
                return "PROP_AND_THIS_OPTIONAL";
            case ELEM_AND_THIS_OPTIONAL:
                return "ELEM_AND_THIS_OPTIONAL";
            case VALUE_AND_THIS_OPTIONAL:
                return "VALUE_AND_THIS_OPTIONAL";
            case CLOSURE_EXPR:
                return "CLOSURE_EXPR";
            case CLOSURE_STMT:
                return "CLOSURE_STMT";
            case CALLSPECIAL:
                return "CALLSPECIAL";
            case CALLSPECIAL_OPTIONAL:
                return "CALLSPECIAL_OPTIONAL";
            case RETUNDEF:
                return "RETUNDEF";
            case GOSUB:
                return "GOSUB";
            case STARTSUB:
                return "STARTSUB";
            case RETSUB:
                return "RETSUB";
            case LINE:
                return "LINE";
            case SHORTNUMBER:
                return "SHORTNUMBER";
            case INTNUMBER:
                return "INTNUMBER";
            case LITERAL_NEW_OBJECT:
                return "LITERAL_NEW_OBJECT";
            case LITERAL_NEW_ARRAY:
                return "LITERAL_NEW_ARRAY";
            case LITERAL_SET:
                return "LITERAL_SET";
            case METHOD_EXPR:
                return "METHOD_EXPR";
            case SPARE_ARRAYLIT:
                return "SPARE_ARRAYLIT";
            case REG_IND_C0:
                return "REG_IND_C0";
            case REG_IND_C1:
                return "REG_IND_C1";
            case REG_IND_C2:
                return "REG_IND_C2";
            case REG_IND_C3:
                return "REG_IND_C3";
            case REG_IND_C4:
                return "REG_IND_C4";
            case REG_IND_C5:
                return "REG_IND_C5";
            case REG_IND1:
                return "LOAD_IND1";
            case REG_IND2:
                return "LOAD_IND2";
            case REG_IND4:
                return "LOAD_IND4";
            case REG_STR_C0:
                return "REG_STR_C0";
            case REG_STR_C1:
                return "REG_STR_C1";
            case REG_STR_C2:
                return "REG_STR_C2";
            case REG_STR_C3:
                return "REG_STR_C3";
            case REG_STR1:
                return "LOAD_STR1";
            case REG_STR2:
                return "LOAD_STR2";
            case REG_STR4:
                return "LOAD_STR4";
            case GETVAR1:
                return "GETVAR1";
            case SETVAR1:
                return "SETVAR1";
            case UNDEF:
                return "UNDEF";
            case ZERO:
                return "ZERO";
            case ONE:
                return "ONE";
            case ENTERDQ:
                return "ENTERDQ";
            case LEAVEDQ:
                return "LEAVEDQ";
            case TAIL_CALL:
                return "TAIL_CALL";
            case LOCAL_CLEAR:
                return "LOCAL_CLEAR";
            case LITERAL_GETTER:
                return "LITERAL_GETTER";
            case LITERAL_SETTER:
                return "LITERAL_SETTER";
            case SETCONST:
                return "SETCONST";
            case SETCONSTVAR:
                return "SETCONSTVAR";
            case SETCONSTVAR1:
                return "SETCONSTVAR1";
            case GENERATOR:
                return "GENERATOR";
            case GENERATOR_END:
                return "GENERATOR_END";
            case DEBUGGER:
                return "DEBUGGER";
            case GENERATOR_RETURN:
                return "GENERATOR_RETURN";
            case YIELD_STAR:
                return "YIELD_STAR";
            case REG_BIGINT_C0:
                return "REG_BIGINT_C0";
            case REG_BIGINT_C1:
                return "REG_BIGINT_C1";
            case REG_BIGINT_C2:
                return "REG_BIGINT_C2";
            case REG_BIGINT_C3:
                return "REG_BIGINT_C3";
            case REG_BIGINT1:
                return "LOAD_BIGINT1";
            case REG_BIGINT2:
                return "LOAD_BIGINT2";
            case REG_BIGINT4:
                return "LOAD_BIGINT4";
            case TEMPLATE_LITERAL_CALLSITE:
                return "TEMPLATE_LITERAL_CALLSITE";
            case LITERAL_KEY_SET:
                return "LITERAL_KEY_SET";
            case IF_NULL_UNDEF:
                return "IF_NULL_UNDEF";
            case IF_NOT_NULL_UNDEF:
                return "IF_NOT_NULL_UNDEF";
            case CALL_ON_SUPER:
                return "CALL_ON_SUPER";
            case DELPROP_SUPER:
                return "DELPROP_SUPER";
            case SPREAD:
                return "SPREAD";
            case OBJECT_REST:
                return "OBJECT_REST";
            case COPY_SCOPE:
                return "COPY_SCOPE";
            case TDZ_SENTINEL:
                return "TDZ_SENTINEL";
            case CHECK_TDZ:
                return "CHECK_TDZ";
            case GLOBAL_LEXICALS:
                return "GLOBAL_LEXICALS";
            case ITER_OPEN:
                return "ITER_OPEN";
            case ITER_STEP:
                return "ITER_STEP";
            case ITER_REST:
                return "ITER_REST";
            case ITER_CLOSE:
                return "ITER_CLOSE";
            case ITER_OPEN_GUARDED:
                return "ITER_OPEN_GUARDED";
            case ITER_GUARD_INIT:
                return "ITER_GUARD_INIT";
            case ITER_GUARD_CLOSE:
                return "ITER_GUARD_CLOSE";
            case REQUIRE_OBJECT:
                return "REQUIRE_OBJECT";
            case CALL_SPREAD:
                return "CALL_SPREAD";
            case CALL_SPREAD_ON_SUPER:
                return "CALL_SPREAD_ON_SUPER";
            case NEW_SPREAD:
                return "NEW_SPREAD";
            case ENUM_CLOSE:
                return "ENUM_CLOSE";
            case ENUM_CLOSE_ABRUPT:
                return "ENUM_CLOSE_ABRUPT";
            case CLASS_DEFINE:
                return "CLASS_DEFINE";
            case DERIVED_THIS:
                return "DERIVED_THIS";
            case SUPER_CALLEE:
                return "SUPER_CALLEE";
            case FN_NAME_ASSIGN:
                return "FN_NAME_ASSIGN";
            case ELEM_KEY:
                return "ELEM_KEY";
            case LITERAL_PROTO:
                return "LITERAL_PROTO";
            case AWAIT_REQUEST:
                return "AWAIT_REQUEST";
            case ASYNC_DELEGATE:
                return "ASYNC_DELEGATE";
            case ASYNC_ITER_OPEN:
                return "ASYNC_ITER_OPEN";
            case ASYNC_ITER_NEXT:
                return "ASYNC_ITER_NEXT";
            case ASYNC_ITER_CLOSE:
                return "ASYNC_ITER_CLOSE";
            case ASYNC_ITER_CLOSE_ABRUPT:
                return "ASYNC_ITER_CLOSE_ABRUPT";
            case IMPORT_CALL:
                return "IMPORT_CALL";
            case FIELD_KEY:
                return "FIELD_KEY";
            case FIELD_KEY_NAMED:
                return "FIELD_KEY_NAMED";
            case CLASS_STATIC:
                return "CLASS_STATIC";
        }

        // icode without name
        throw new IllegalStateException(String.valueOf(bytecode));
    }

    static boolean validIcode(int icode) {
        return MIN_ICODE <= icode && icode <= 0;
    }

    static boolean validTokenCode(int token) {
        return Token.FIRST_BYTECODE_TOKEN <= token && token <= Token.LAST_BYTECODE_TOKEN;
    }

    static boolean validBytecode(int bytecode) {
        return validIcode(bytecode) || validTokenCode(bytecode);
    }
}
