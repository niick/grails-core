package org.codehaus.groovy.grails.orm.hibernate;

import org.codehaus.groovy.grails.commons.*
import org.codehaus.groovy.grails.commons.test.*

class ValidatePersistentMethodTests extends AbstractGrailsHibernateTests {

	void testToOneCascadingValidation() {
        def bookClass = ga.getDomainClass("Book")
        def authorClass = ga.getDomainClass("Author")
        def addressClass = ga.getDomainClass("Address")

        def book = bookClass.newInstance()

        assert !book.validate()
        assert !book.validate(deepValidate:false)

        book.title = "Foo"

        assert !book.validate()
        assert !book.validate(deepValidate:false)

        def author = authorClass.newInstance()
        book.author = author

        assert !book.validate()
        assert book.validate(deepValidate:false)

        author.name = "Bar"

        assert !book.validate()
        assert book.validate(deepValidate:false)

        def address = addressClass.newInstance()

        author.address = address

        assert !book.validate()
        assert book.validate(deepValidate:false)

        address.location = "Foo Bar"

        assert book.validate()
        assert book.validate(deepValidate:false)
	}

	void testToManyCascadingValidation() {
        def bookClass = ga.getDomainClass("Book")
        def authorClass = ga.getDomainClass("Author")
        def addressClass = ga.getDomainClass("Address")

        def author = authorClass.newInstance()

        assert !author.validate()
        author.name = "Foo"

        assert !author.validate()
        assert !author.validate(deepValidate:false)

        def address = addressClass.newInstance()
        author.address = address

        assert !author.validate()
        assert author.validate(deepValidate:false)

        address.location = "Foo Bar"
        assert author.validate()
        assert author.validate(deepValidate:false)

        def book = bookClass.newInstance()

        author.addToBooks(book)
        assert !author.validate()
        assert author.validate(deepValidate:false)

        book.title = "TDGTG"
        assert author.validate()
        assert author.validate(deepValidate:false)

	}

	void onSetUp() {
		this.gcl.parseClass('''
class Book {
    Long id
    Long version
    String title
    Author author
    static constraints = {
       title(blank:false, size:1..255)
       author(nullable:false)
    }
}
class Author {
   Long id
   Long version
   String name
   Address address
   Set books = new HashSet()
   static hasMany = [books:Book]
   static constraints = {
        address(nullable:false)
        name(size:1..255, blank:false)
   }
}
class Address {
    Long id
    Long version
    Author author
    String location
    static constraints = {
       author(nullable:false)
       location(blank:false)
    }
}
'''
		)
	}
	
	void onTearDown() {
		
	}
}