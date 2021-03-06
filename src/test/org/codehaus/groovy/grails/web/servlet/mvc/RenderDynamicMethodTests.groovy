package org.codehaus.groovy.grails.web.servlet.mvc

import org.codehaus.groovy.grails.commons.test.*
import org.codehaus.groovy.grails.commons.*
import org.codehaus.groovy.grails.commons.spring.*
import org.codehaus.groovy.grails.plugins.*
import org.springframework.web.context.request.*
import org.codehaus.groovy.grails.web.servlet.mvc.*
import org.codehaus.groovy.grails.web.servlet.*
import org.springframework.mock.web.*
import org.springframework.validation.*
import org.springframework.web.servlet.*

@SuppressWarnings("unused")
class RenderDynamicMethodTests extends AbstractGrailsControllerTests {

    protected void tearDown() {
        super.tearDown();
        ConfigurationHolder.config = null
    }


    void onSetUp() {
        def config = gcl.parseClass("grails.json.legacy.builder=false")
        ConfigurationHolder.config = new ConfigSlurper().parse(config)
				gcl.parseClass(
		"""
		class TestController {
		   def renderText = {
		        render "text"
		   }

		   def renderStreamCharBuffer = {
				def writer = new org.codehaus.groovy.grails.web.pages.FastStringWriter()
				writer.write("text")
				render writer.buffer
		   }

		   def renderGString = {
				render "${'te' + 'xt'}"
		   }

		   def renderTextWithContentType = {
		        render(text:"<foo>bar</foo>",contentType:"text/xml", encoding:"utf-16")
           }

           def renderXml = {
                render(contentType:"text/xml") {
                    foo {
                        bar("hello")
                    }
                }
            }

            def renderJSON = {
                render(contentType:"application/json") {
                    foo = [ { bar = "hello" } ]                    
                }
            }
            def renderView ={
                render(view:'foo')
	    	}
            def renderXmlView = {
                render(view:'foo', contentType:'text/xml')
	    	}
            def renderXmlUtf16View = {
                render(view:'foo', contentType:'text/xml', encoding:'utf-16')
	    	}
        }
		""")
	}


    void testRenderView() {
        def testCtrl = ga.getControllerClass("TestController").newInstance()

        testCtrl.renderView()

        assertEquals '/test/foo', testCtrl.modelAndView.viewName
        assertEquals 'text/html;charset=utf-8', response.contentType

    }
    void testRenderText() {
        def testCtrl = ga.getControllerClass("TestController").newInstance()

        testCtrl.renderText()
        assertEquals "text/html;charset=utf-8", response.contentType
        assertEquals "text", response.contentAsString
    }

	void testRenderStreamCharBuffer() {
		def testCtrl = ga.getControllerClass("TestController").newInstance()
		
		testCtrl.renderStreamCharBuffer()
		assertEquals "text/html;charset=utf-8", response.contentType
		assertEquals "text", response.contentAsString
	}
	
	void testRenderGString() {
		def testCtrl = ga.getControllerClass("TestController").newInstance()
		
		testCtrl.renderGString()
		assertEquals "text/html;charset=utf-8", response.contentType
		assertEquals "text", response.contentAsString
	}
		
	void testRenderTextWithContentType() {
        def testCtrl = ga.getControllerClass("TestController").newInstance()

        testCtrl.renderTextWithContentType()
        assertEquals "text/xml;charset=utf-16", response.contentType
        assertEquals "<foo>bar</foo>", response.contentAsString

    }

    void testRenderXml() {
        def testCtrl = ga.getControllerClass("TestController").newInstance()

        testCtrl.renderXml()
        assertEquals "text/xml;charset=utf-8", response.contentType
        assertEquals "<foo><bar>hello</bar></foo>", response.contentAsString

    }

    void testRenderJSON() {
        def testCtrl = ga.getControllerClass("TestController").newInstance()

        testCtrl.renderJSON()
        assertEquals "application/json;charset=UTF-8", response.contentType
        assertEquals '{"foo":[{"bar":"hello"}]}', response.contentAsString
    }
}
