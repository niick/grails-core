/* Copyright 2004-2005 the original author or authors.
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
package org.codehaus.groovy.grails.commons;


import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.unmodifiableMap;
import grails.util.GrailsNameUtils;
import groovy.lang.GroovyObject;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.grails.exceptions.GrailsDomainException;
import org.codehaus.groovy.grails.exceptions.InvalidPropertyException;
import org.springframework.validation.Validator;

/**
 * @author Graeme Rocher
 * @since 05-Jul-2005
 */
public class DefaultGrailsDomainClass extends AbstractGrailsClass  implements GrailsDomainClass {

    private GrailsDomainClassProperty identifier;
    private GrailsDomainClassProperty version;
    private GrailsDomainClassProperty[] properties;
    private GrailsDomainClassProperty[] persistentProperties;
    private Map<String, GrailsDomainClassProperty> propertyMap;
    private Map relationshipMap;
    private Map hasOneMap;

    private Map constraints;
    private Map mappedBy;
    private Validator validator;
    private String mappingStrategy = GrailsDomainClass.GORM;
    private List owners = new ArrayList();
    private boolean root = true;
    private Set subClasses = new HashSet();
    private Collection embedded;
    private Map<String, Object> defaultConstraints;

    public DefaultGrailsDomainClass(Class clazz, Map<String, Object> defaultConstraints) {
        super(clazz, "");
        PropertyDescriptor[] propertyDescriptors = getPropertyDescriptors();

        if(!clazz.getSuperclass().equals( GroovyObject.class ) &&
           !clazz.getSuperclass().equals(Object.class) &&
           !Modifier.isAbstract(clazz.getSuperclass().getModifiers())) {
            this.root = false;
        }
        this.propertyMap = new LinkedHashMap<String, GrailsDomainClassProperty>();
        this.relationshipMap = getAssociationMap();
        this.embedded = getEmbeddedList();
        this.defaultConstraints = defaultConstraints;         


        // get mapping strategy by setting        
        this.mappingStrategy = getStaticPropertyValue(GrailsDomainClassProperty.MAPPING_STRATEGY, String.class);
        if(this.mappingStrategy == null) {
        	this.mappingStrategy = GORM;
        }

        // get any mappedBy settings
        this.mappedBy = getStaticPropertyValue(GrailsDomainClassProperty.MAPPED_BY, Map.class);
        this.hasOneMap = getStaticPropertyValue(GrailsDomainClassProperty.HAS_ONE, Map.class);
        if(hasOneMap == null) hasOneMap = EMPTY_MAP;
        if(this.mappedBy == null)this.mappedBy = EMPTY_MAP;

        // establish the owners of relationships
        establishRelationshipOwners();

        // First go through the properties of the class and create domain properties
        // populating into a map
        populateDomainClassProperties(propertyDescriptors);

        // if no identifier property throw exception
        if(this.identifier == null) {
            throw new GrailsDomainException("Identity property not found, but required in domain class ["+getFullName()+"]" );
        }
        // if no version property throw exception
        if(this.version == null) {
            throw new GrailsDomainException("Version property not found, but required in domain class ["+getFullName()+"]" );
        }
        // set properties from map values
        this.properties = this.propertyMap.values().toArray( new GrailsDomainClassProperty[this.propertyMap.size()] );

        // establish relationships
        establishRelationships();

        // set persistent properties
        establishPersistentProperties();

    }
    public DefaultGrailsDomainClass(Class clazz) {
        this(clazz, null);
    }


    public boolean hasSubClasses() {
        return getSubClasses().size() > 0;
    }



    /**
	 * calculates the persistent properties from the evaluated properties
	 */
	private void establishPersistentProperties() {
		Collection<GrailsDomainClassProperty> tempList = new ArrayList<GrailsDomainClassProperty>();
        for (Object o : this.propertyMap.values()) {
            GrailsDomainClassProperty currentProp = (GrailsDomainClassProperty) o;
            if (currentProp.getType() != Object.class && currentProp.isPersistent() && !currentProp.isIdentity() && !currentProp.getName().equals(GrailsDomainClassProperty.VERSION)) {
                tempList.add(currentProp);
            }
        }
        this.persistentProperties = tempList.toArray( new GrailsDomainClassProperty[tempList.size()]);
	}

	/**
	 * Evaluates the belongsTo property to find out who owns who
	 */
	private void establishRelationshipOwners() {
		Class belongsTo = getStaticPropertyValue(GrailsDomainClassProperty.BELONGS_TO, Class.class);
        if(belongsTo == null) {
            List ownersProp = getStaticPropertyValue(GrailsDomainClassProperty.BELONGS_TO, List.class);
            if(ownersProp != null) {
                this.owners = ownersProp;
            }
            else {
                Map ownersMap = getStaticPropertyValue(GrailsDomainClassProperty.BELONGS_TO, Map.class);
                if(ownersMap!=null) {
                    this.owners = new ArrayList(ownersMap.values());
                }
            }
        }
        else {
            this.owners = new ArrayList();
            this.owners.add(belongsTo);
        }
	}

	/**
	 * Populates the domain class properties map
	 *
	 * @param propertyDescriptors The property descriptors
	 */
	private void populateDomainClassProperties(PropertyDescriptor[] propertyDescriptors) {
        for (PropertyDescriptor descriptor : propertyDescriptors) {

            // ignore certain properties
            if (GrailsDomainConfigurationUtil.isNotConfigurational(descriptor)) {


                GrailsDomainClassProperty property = new DefaultGrailsDomainClassProperty(this, descriptor);
                this.propertyMap.put(property.getName(), property);

                if (property.isIdentity()) {
                    this.identifier = property;
                }
                else if (property.getName().equals(GrailsDomainClassProperty.VERSION)) {
                    this.version = property;
                }
            }

        }
	}

	/**
	 * Retrieves the association map
	 */
    public Map getAssociationMap() {
        if(this.relationshipMap == null) {
            relationshipMap = getStaticPropertyValue(GrailsDomainClassProperty.HAS_MANY, Map.class);
            if(relationshipMap == null)
                this.relationshipMap = new HashMap();

            Class theClass = getClazz();
            while(theClass != Object.class) {
                theClass = theClass.getSuperclass();
                ClassPropertyFetcher propertyFetcher = ClassPropertyFetcher.forClass(theClass);
                Map superRelationshipMap = propertyFetcher.getStaticPropertyValue(GrailsDomainClassProperty.HAS_MANY, Map.class);
                if(superRelationshipMap != null && !superRelationshipMap.equals(relationshipMap)) {
                    relationshipMap.putAll(superRelationshipMap);
                }
            }
        }
        return this.relationshipMap;
    }

    /**
     * Retrieves the list of known embedded component types
     *
     * @return A list of embedded components
     */
    private Collection getEmbeddedList() {
        Collection potentialList = getStaticPropertyValue(GrailsDomainClassProperty.EMBEDDED, Collection.class);
        return potentialList != null ? potentialList : EMPTY_LIST;
    }


    /**
     * Calculates the relationship type based other types referenced
     *
     */
    private void establishRelationships() {
        for (Object o : this.propertyMap.values()) {
            DefaultGrailsDomainClassProperty currentProp = (DefaultGrailsDomainClassProperty) o;
            if(!currentProp.isPersistent()) continue;

            Class currentPropType = currentProp.getType();
            // establish if the property is a one-to-many
            // if it is a Set and there are relationships defined
            // and it is defined as persistent
            if (Collection.class.isAssignableFrom(currentPropType) || Map.class.isAssignableFrom(currentPropType)) {

                establishRelationshipForCollection(currentProp);
            }
            // otherwise if the type is a domain class establish relationship
            else if (DomainClassArtefactHandler.isDomainClass(currentPropType) &&
                    currentProp.isPersistent()) {

                establishDomainClassRelationship(currentProp);
            }
            else if (embedded.contains(currentProp.getName())) {
                establishDomainClassRelationship(currentProp);
            }

        }

    }

    /**
     * Establishes a relationship for a java.util.Set
     *
     * @param property The collection property
     */
    private void  establishRelationshipForCollection(DefaultGrailsDomainClassProperty property) {
        // is it a relationship
        Class relatedClassType = getRelatedClassType( property.getName() );


        if(relatedClassType != null) {
            // set the referenced type in the property
            property.setReferencedPropertyType(relatedClassType);

            // if the related type is a domain class
            // then figure out what kind of relationship it is
            if(DomainClassArtefactHandler.isDomainClass(relatedClassType)) {


                // check the relationship defined in the referenced type
                // if it is also a Set/domain class etc.
                Map relatedClassRelationships = GrailsDomainConfigurationUtil.getAssociationMap(relatedClassType);
                Class relatedClassPropertyType = null;

                // First check whether there is an explicit relationship
                // mapping for this property (as provided by "mappedBy").
                String mappingProperty = (String)this.mappedBy.get(property.getName());
                if(!StringUtils.isBlank(mappingProperty)) {
                    // First find the specified property on the related
                    // class, if it exists.
                    PropertyDescriptor pd = findProperty(GrailsClassUtils.getPropertiesOfType(relatedClassType, getClazz()), mappingProperty);

                    // If a property of the required type does not exist,
                    // search for any collection properties on the related
                    // class.
                    if(pd == null) pd = findProperty(GrailsClassUtils.getPropertiesAssignableToType(relatedClassType, Collection.class), mappingProperty);

                    // We've run out of options. The given "mappedBy"
                    // setting is invalid.
                    if(pd == null)
                        throw new GrailsDomainException("Non-existent mapping property ["+mappingProperty+"] specified for property ["+property.getName()+"] in class ["+getClazz()+"]");

                    // Tie the properties together.
                    relatedClassPropertyType = pd.getPropertyType();
                    property.setReferencePropertyName(pd.getName());
                }
                else {
                    // if the related type has a relationships map it may be a many-to-many
                    // figure out if there is a many-to-many relationship defined
                    if(	isRelationshipManyToMany(property, relatedClassType, relatedClassRelationships)) {
                        String relatedClassPropertyName = null;
                        Map relatedClassMappedBy = GrailsDomainConfigurationUtil.getMappedByMap(relatedClassType);
                        // retrieve the relationship property
                        for (Object o : relatedClassRelationships.keySet()) {
                            String currentKey = (String) o;
                            String mappedByProperty = (String) relatedClassMappedBy.get(currentKey);
                            if(mappedByProperty != null && !mappedByProperty.equals(property.getName())) continue;
                            Class currentClass = (Class) relatedClassRelationships.get(currentKey);
                            if (currentClass.isAssignableFrom(getClazz())) {
                                relatedClassPropertyName = currentKey;
                                break;
                            }
                        }

                        // if there is one defined get the type
                        if(relatedClassPropertyName != null) {
                            relatedClassPropertyType = GrailsClassUtils.getPropertyType( relatedClassType, relatedClassPropertyName);
                        }
                    }
                    // otherwise figure out if there is a one-to-many relationship by retrieving any properties that are of the related type
                    // if there is more than one property then (for the moment) ignore the relationship
                    if(relatedClassPropertyType == null) {
                        PropertyDescriptor[] descriptors = GrailsClassUtils.getPropertiesOfType(relatedClassType, getClazz());

                        if(descriptors.length == 1) {
                            relatedClassPropertyType = descriptors[0].getPropertyType();
                            property.setReferencePropertyName(descriptors[0].getName());
                        }
                        else if(descriptors.length > 1) {
                            // try now to use the class name by convention
                            String classPropertyName = getPropertyName();
                            PropertyDescriptor pd = findProperty(descriptors, classPropertyName);
                            if(pd == null) {
                                throw new GrailsDomainException("Property ["+property.getName()+"] in class ["+getClazz()+"] is a bidirectional one-to-many with two possible properties on the inverse side. "+
                                        "Either name one of the properties on other side of the relationship ["+classPropertyName+"] or use the 'mappedBy' static to define the property " +
                                        "that the relationship is mapped with. Example: static mappedBy = ["+property.getName()+":'myprop']");

                            }
                            relatedClassPropertyType = pd.getPropertyType();
                            property.setReferencePropertyName(pd.getName());
                        }
                    }
                }

                establishRelationshipForSetToType(property,relatedClassPropertyType);
                // if its a many-to-many figure out the owning side of the relationship
                if(property.isManyToMany()) {
                	establishOwnerOfManyToMany(property, relatedClassType);
                }
            }
            // otherwise set it to not persistent as you can't persist
            // relationships to non-domain classes
            else {
                property.setBasicCollectionType(true);
            }
        }
        else if(!Map.class.isAssignableFrom(property.getType())) {
            // no relationship defined for set.
            // set not persistent
            property.setPersistant(false);
        }

    }

    
    /**
     * Finds a property type is an array of descriptors for the given property name
     *
     * @param descriptors The descriptors
     * @param propertyName The property name
     * @return The Class or null
     */
    private PropertyDescriptor findProperty(PropertyDescriptor[] descriptors, String propertyName) {
		PropertyDescriptor d = null;
        for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor.getName().equals(propertyName)) {
                d = descriptor;
                break;
            }
        }
		return d;
	}

    /**
     * Find out if the relationship is a many-to-many
     *
     * @param property The property
     * @param relatedClassType The related type
     * @param relatedClassRelationships The related types relationships
     * @return True if the relationship is a many-to-many
     */
	private boolean isRelationshipManyToMany(DefaultGrailsDomainClassProperty property, Class relatedClassType, Map relatedClassRelationships) {
		return relatedClassRelationships != null &&
		    !relatedClassRelationships.isEmpty() &&
		     !relatedClassType.equals(property.getDomainClass().getClazz());
	}

    /**
     * Inspects a related classes' ownership settings against this properties class' ownership
     * settings to find out who owns a many-to-many relationship
     *
     * @param property The property
     * @param relatedClassType The related type
     */
    private void establishOwnerOfManyToMany(DefaultGrailsDomainClassProperty property, Class relatedClassType) {
    	ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(relatedClassType);
    	Object relatedBelongsTo = cpf.getPropertyValue(GrailsDomainClassProperty.BELONGS_TO);
    	boolean owningSide = false;
    	boolean relatedOwner = this.owners.contains(relatedClassType);
    	final Class propertyClass = property.getDomainClass().getClazz();
    	if(relatedBelongsTo instanceof Collection) {
			owningSide = ((Collection)relatedBelongsTo).contains(propertyClass);
    	}
    	else if (relatedBelongsTo != null) {
    		owningSide = relatedBelongsTo.equals(propertyClass);
    	}
    	property.setOwningSide(owningSide);
    	if(relatedOwner && property.isOwningSide()) {
    		throw new GrailsDomainException("Domain classes ["+propertyClass+"] and ["+relatedClassType+"] cannot own each other in a many-to-many relationship. Both contain belongsTo definitions that reference each other.");
    	}
    	else if(!relatedOwner && !property.isOwningSide() && !(property.isCircular() && property.isManyToMany())) {
    		throw new GrailsDomainException("No owner defined between domain classes ["+propertyClass+"] and ["+relatedClassType+"] in a many-to-many relationship. Example: def belongsTo = "+relatedClassType.getName());
    	}
	}

	/**
     * Establishes whether the relationship is a bi-directional or uni-directional one-to-many
     * and applies the appropriate settings to the specified property
     *
     * @param property The property to apply settings to
     * @param relatedClassPropertyType The related type
     */
    private void establishRelationshipForSetToType(DefaultGrailsDomainClassProperty property, Class relatedClassPropertyType) {

        if(relatedClassPropertyType == null) {
            // uni-directional one-to-many
            property.setOneToMany(true);
            property.setBidirectional(false);
        }
        else if( Collection.class.isAssignableFrom(relatedClassPropertyType) || Map.class.isAssignableFrom(relatedClassPropertyType)  ){
            // many-to-many
            property.setManyToMany(true);
            property.setBidirectional(true);
        }
        else if(DomainClassArtefactHandler.isDomainClass(relatedClassPropertyType)) {
            // bi-directional one-to-many
            property.setOneToMany( true );
            property.setBidirectional( true );
        }
    }


    /**
     * Establish relationship with related domain class
     *
     * @param property Establishes a relationship between this class and the domain class property
     */
    private void establishDomainClassRelationship(DefaultGrailsDomainClassProperty property) {
        Class propType = property.getType();
        if(embedded.contains(property.getName())) {
            property.setEmbedded(true);
            return;
        }

        // establish relationship to type
        Map relatedClassRelationships = GrailsDomainConfigurationUtil.getAssociationMap(propType);
        Map mappedBy = GrailsDomainConfigurationUtil.getMappedByMap(propType);


        Class relatedClassPropertyType = null;

        // if there is a relationships map use that to find out
        // whether it is mapped to a Set
        if(	relatedClassRelationships != null &&
            !relatedClassRelationships.isEmpty() ) {


        	String relatedClassPropertyName = findOneToManyThatMatchesType(property, relatedClassRelationships);
            PropertyDescriptor[] descriptors = GrailsClassUtils.getPropertiesOfType(getClazz(), property.getType());

            // if there is only one property on many-to-one side of the relationship then
            // try to establish if it is bidirectional
            if(descriptors.length == 1 && isNotMappedToDifferentProperty(property,relatedClassPropertyName, mappedBy)) {
                if(!StringUtils.isBlank(relatedClassPropertyName)) {
                    property.setReferencePropertyName(relatedClassPropertyName);
                    // get the type of the property
                    relatedClassPropertyType = GrailsClassUtils.getPropertyType( propType, relatedClassPropertyName );
                }
            }
            // if there is more than one property on the many-to-one side then we need to either
            // find out if there is a mappedBy property or whether a convention is used to decide
            // on the mapping property
            else if(descriptors.length > 1) {
            	if(mappedBy.containsValue(property.getName())) {
                    for (Object o : mappedBy.keySet()) {
                        String mappedByPropertyName = (String) o;
                        if (property.getName().equals(mappedBy.get(mappedByPropertyName))) {
                            Class mappedByRelatedType = (Class) relatedClassRelationships.get(mappedByPropertyName);
                            if (mappedByRelatedType != null && propType.isAssignableFrom(mappedByRelatedType))
                                relatedClassPropertyType = GrailsClassUtils.getPropertyType(propType, mappedByPropertyName);
                        }
                    }
            	}
            	else {
            		String classNameAsProperty = GrailsNameUtils.getPropertyName(propType);
            		if(property.getName().equals(classNameAsProperty) && !mappedBy.containsKey(relatedClassPropertyName)) {
            			relatedClassPropertyType = GrailsClassUtils.getPropertyType( propType, relatedClassPropertyName );
            		}
            	}
            }
        }
        // otherwise retrieve all the properties of the type from the associated class
        if(relatedClassPropertyType == null) {
            PropertyDescriptor[] descriptors = GrailsClassUtils.getPropertiesOfType(propType, getClazz());

            // if there is only one then the association is established
            if(descriptors.length == 1) {
                relatedClassPropertyType = descriptors[0].getPropertyType();
            }
        }


        //	establish relationship based on this type
        establishDomainClassRelationshipToType( property, relatedClassPropertyType );
    }

    private boolean isNotMappedToDifferentProperty(GrailsDomainClassProperty property, String relatedClassPropertyName, Map mappedBy) {
        String mappedByForRelation = (String)mappedBy.get(relatedClassPropertyName);
        if(mappedByForRelation == null) return true;
        else if(!property.getName().equals(mappedByForRelation)) return false;
        return true;
    }

    private String findOneToManyThatMatchesType(DefaultGrailsDomainClassProperty property, Map relatedClassRelationships) {
		String relatedClassPropertyName = null;

        for (Object o : relatedClassRelationships.keySet()) {
            String currentKey = (String) o;
            Class currentClass = (Class) relatedClassRelationships.get(currentKey);

            if (property.getDomainClass().getClazz().getName().equals(currentClass.getName())) {
                relatedClassPropertyName = currentKey;

                break;
            }
        }
		return relatedClassPropertyName;
	}

    private void establishDomainClassRelationshipToType(DefaultGrailsDomainClassProperty property, Class relatedClassPropertyType) {
        // uni-directional one-to-one

        if(relatedClassPropertyType == null) {

            if(hasOneMap.containsKey(property.getName())) {
                property.setHasOne(true);
            }
            property.setOneToOne(true);
            property.setBidirectional(false);
        }
        // bi-directional many-to-one
        else if(Collection.class.isAssignableFrom(relatedClassPropertyType)||Map.class.isAssignableFrom(relatedClassPropertyType)) {
            property.setManyToOne(true);
            property.setBidirectional(true);
        }
        // bi-directional one-to-one
        else if(DomainClassArtefactHandler.isDomainClass(relatedClassPropertyType)) {
            if(hasOneMap.containsKey(property.getName())) {
                property.setHasOne(true);
            }
            
            property.setOneToOne(true);
            if(!getClazz().equals(relatedClassPropertyType))
                property.setBidirectional(true);
        }
    }


    public boolean isOwningClass(Class domainClass) {
        return this.owners.contains(domainClass);
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.domain.GrailsDomainClass#getProperties()
      */
    public GrailsDomainClassProperty[] getProperties() {
        return this.properties;
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.domain.GrailsDomainClass#getIdentifier()
      */
    public GrailsDomainClassProperty getIdentifier() {
        return this.identifier;
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.domain.GrailsDomainClass#getVersion()
      */
    public GrailsDomainClassProperty getVersion() {
        return this.version;
    }

    /**
     * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#getPersistantProperties()
     * @deprecated
     */
    public GrailsDomainClassProperty[] getPersistantProperties() {
        return this.persistentProperties;
    }

    public GrailsDomainClassProperty[] getPersistentProperties() {
        return this.persistentProperties;
    }

    /* (non-Javadoc)
    * @see org.codehaus.groovy.grails.domain.GrailsDomainClass#getPropertyByName(java.lang.String)
    */
    public GrailsDomainClassProperty getPropertyByName(String name) {
        if(this.propertyMap.containsKey(name)) {
            return this.propertyMap.get(name);
        }
        else {
            throw new InvalidPropertyException("No property found for name ["+name+"] for class ["+getClazz()+"]");
        }
    }
    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.domain.GrailsDomainClass#getFieldName(java.lang.String)
      */
    public String getFieldName(String propertyName) {
        return getPropertyByName(propertyName).getFieldName();
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.commons.AbstractGrailsClass#getName()
      */
    public String getName() {
        return ClassUtils.getShortClassName(super.getName());
    }
    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.domain.GrailsDomainClass#isOneToMany(java.lang.String)
      */
    public boolean isOneToMany(String propertyName) {
        return getPropertyByName(propertyName).isOneToMany();
    }
    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.domain.GrailsDomainClass#isManyToOne(java.lang.String)
      */
    public boolean isManyToOne(String propertyName) {
        return getPropertyByName(propertyName).isManyToOne();
    }

    protected Object getPropertyOrStaticPropertyOrFieldValue(String name, Class type) {
        return super.getPropertyOrStaticPropertyOrFieldValue(name,type);
    }
    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#getRelationshipType(java.lang.String)
      */
    public Class getRelatedClassType(String propertyName) {
        return (Class)this.relationshipMap.get(propertyName);
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#getPropertyName()
      */
    public String getPropertyName() {
        return GrailsNameUtils.getPropertyNameRepresentation(getClazz());
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#isBidirectional()
      */
    public boolean isBidirectional(String propertyName) {
        return getPropertyByName(propertyName).isBidirectional();
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#getConstraints()
      */
    public Map getConstrainedProperties() {
        if(this.constraints==null) {
            initializeConstraints();
        }
        return unmodifiableMap(this.constraints);
    }

    private void initializeConstraints() {
        // process the constraints
        if(defaultConstraints != null) {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), this.persistentProperties, defaultConstraints);
        }
        else {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), this.persistentProperties);
        }
    }

    /* (non-Javadoc)
    * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#getValidator()
    */
    public Validator getValidator() {
        return this.validator;
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#setValidator(Validator validator)
      */
    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.commons.GrailsDomainClass#getMappedBy()
      */
    public String getMappingStrategy() {
    	return this.mappingStrategy;
    }

    public boolean isRoot() {
        return this.root;
    }

    public Set getSubClasses() {
        return this.subClasses;
    }

    public void refreshConstraints() {
        if(defaultConstraints!=null) {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), this.persistentProperties, defaultConstraints);
        }
        else {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), this.persistentProperties);
        }

        // Embedded components have their own ComponentDomainClass
        // instance which won't be refreshed by the application.
        // So, we have to do it here.
        for (GrailsDomainClassProperty property : this.persistentProperties) {
            if (property.isEmbedded()) {
                property.getComponent().refreshConstraints();
            }
        }
    }

    public Map getMappedBy() {
        return mappedBy;
    }

    public boolean hasPersistentProperty(String propertyName) {
        for (GrailsDomainClassProperty persistentProperty : persistentProperties) {
            if (persistentProperty.getName().equals(propertyName)) return true;
        }
        return false;
    }

    public void setMappingStrategy(String strategy) {
        this.mappingStrategy = strategy;
    }
}
