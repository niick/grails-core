package org.codehaus.groovy.grails.validation;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;

/**
 * Test cases for custom 'validator' constraint.
 *
 * @author Sergey Nebolsin (<a href="mailto:nebolsin@gmail.com"/>)
 */
public class ValidatorConstraintTests extends AbstractConstraintTests {
    private static final String PROP_NAME = "firstName";
    private GroovyShell shell = new GroovyShell();

    protected Class getConstraintClass() {
        return ValidatorConstraint.class;
    }

    private Closure getClosure( String code ) {
        return (Closure) shell.evaluate( code );
    }

    protected Constraint getConstraint( String closure ) {
        return super.getConstraint( "testString", getClosure(closure) );
    }

    public void testBooleanReturn()  {
        testConstraintMessageCodes(
                getConstraint("{val,obj -> return false}"),
                "test",
                new String[] { "testClass.testString.validator.error","testClass.testString.validator.invalid"},
                new Object[] { "testString", TestClass.class, "test" }
        );

        testConstraintPassed(
                getConstraint( "{val,obj -> return true}" ),
                "test"
        );

        testConstraintDefaultMessage(
                getConstraint("{val,obj -> return false}"),
                "test",
                "Property [{0}] of class [{1}] with value [{2}] does not pass custom validation"
        );
    }


    public void testStringReturn() {
        testConstraintMessageCode(
                getConstraint("{val,obj -> return 'test.message'}"),
                "test",
                "testClass.testString.test.message",
                new Object[] { "testString", TestClass.class, "test" }
        );

        try {
            testConstraintFailed(
                    getConstraint("{val,obj -> return 123L}"),
                    "test"
            );
            fail("Validator constraint must throw an exception about wrong closure return");
        } catch( IllegalArgumentException iae ) {
            // Greate
        }
    }

    public void testListReturn() {
        testConstraintMessageCode(
                getConstraint("{val,obj -> return ['test.message', 'arg', 123L]}"),
                "test",
                "testClass.testString.test.message",
                new Object[] { "testString", TestClass.class, "test", "arg", new Long(123) }
        );
        try {
            testConstraintFailed(
                    getConstraint("{val,obj -> return [123L,'arg1','arg2']}"),
                    "test"
            );
            fail("Validator constraint must throw an exception about wrong closure return");
        } catch( IllegalArgumentException iae ) {
            // Greate
        }
    }

    public void testConstraintCreation() {
        Constraint validatorConstraint = new ValidatorConstraint();
        assertEquals( ConstrainedProperty.VALIDATOR_CONSTRAINT, validatorConstraint.getName() );
        assertTrue( validatorConstraint.supports( TestClass.class ));
        assertFalse( validatorConstraint.supports( null ));

        validatorConstraint.setOwningClass( TestClass.class );
        validatorConstraint.setPropertyName( PROP_NAME );

        try {
            getConstraint( "testString", "Test");
            fail("ValidatorConstraint must throw an exception for non-closure parameter.");
        } catch ( IllegalArgumentException iae ) {
            // Great since validator constraint only applicable for Closure parameter
        }

        try {
            getConstraint( "{ param1, param2, param3 -> return true}" );
            fail("ValidatorConstraint must throw exception about closure with more that 2 params");
        } catch ( IllegalArgumentException iae ) {
            // Great since validator constraint only applicable for Closure's with 1 or 2 params
        }

        try {
            getConstraint( "{ Long param1, param2 -> return true}" );
            fail("ValidatorConstraint must throw exception about wrong closure param type");
        } catch ( IllegalArgumentException iae ) {
            // Great
        }

        try {
            getConstraint( "{ param1, String param2 -> return true}" );
            fail("ValidatorConstraint must throw exception about wrong closure param type");
        } catch ( IllegalArgumentException iae ) {
            // Great
        }
    }
}