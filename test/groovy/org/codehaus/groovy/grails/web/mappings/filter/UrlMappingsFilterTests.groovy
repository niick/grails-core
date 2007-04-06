/* Copyright 2004-2005 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.mapping.filter;

import junit.framework.TestCase;
import org.springframework.mock.web.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.codehaus.groovy.grails.support.MockApplicationContext;
import org.codehaus.groovy.grails.web.mapping.UrlMappingsHolder;
import org.codehaus.groovy.grails.web.mapping.UrlMappingEvaluator;
import org.codehaus.groovy.grails.web.mapping.DefaultUrlMappingEvaluator;
import org.codehaus.groovy.grails.web.mapping.DefaultUrlMappingsHolder;

import java.util.List;

/**
 * Tests for the UrlMappingsFilter
 *
 * @author Graeme Rocher
 * @since 0.5
 *        <p/>
 *        Created: Mar 6, 2007
 *        Time: 5:51:17 PM
 */
public class UrlMappingsFilterTests extends GroovyTestCase {

    def mappingScript = '''
mappings {
  "/$id/$year?/$month?/$day?" {
        controller = "blog"
        action = "show"
        constraints {
            year(matches:/\\d{4}/)
            month(matches:/\\d{2}/)
        }
  }

  "/product/$name" {
        controller = "product"
        action = "show"
  }
}
'''

    void testUrlMappingFilter() {

        def webRequest = grails.util.GrailsWebUtil.bindMockWebRequest()
        def servletContext = new MockServletContext();

        def appCtx = new MockApplicationContext();

        def evaluator = new DefaultUrlMappingEvaluator();

        def mappings = evaluator.evaluateMappings(new ByteArrayResource(mappingScript.getBytes()));
        appCtx.registerMockBean(UrlMappingsHolder.BEAN_ID, new DefaultUrlMappingsHolder(mappings));

        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,appCtx);

        def request = new MockHttpServletRequest();
        def response = new MockHttpServletResponse()
        request.setRequestURI("/my_entry/2007/06/01");

        def filter = new UrlMappingsFilter();

        filter.init(new MockFilterConfig(servletContext));

        filter.doFilterInternal(request, response,null);

        assertEquals "/grails/blog/show.dispatch", response.includedUrl
        assertEquals "my_entry", webRequest.params.id
        assertEquals "2007", webRequest.params.year
        assertEquals "06", webRequest.params.month
        assertEquals "01", webRequest.params.day
    }
}