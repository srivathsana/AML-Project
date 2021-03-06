/******************************************************************************
* Copyright 2013-2015 LASIGE                                                  *
*                                                                             *
* Licensed under the Apache License, Version 2.0 (the "License"); you may     *
* not use this file except in compliance with the License. You may obtain a   *
* copy of the License at http://www.apache.org/licenses/LICENSE-2.0           *
*                                                                             *
* Unless required by applicable law or agreed to in writing, software         *
* distributed under the License is distributed on an "AS IS" BASIS,           *
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    *
* See the License for the specific language governing permissions and         *
* limitations under the License.                                              *
*                                                                             *
*******************************************************************************
* An Ontology object, loaded using the OWL API.                               *
*                                                                             *
* @author Daniel Faria                                                        *
* @date 26-05-2015                                                            *
******************************************************************************/
package aml.ontology;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import uk.ac.manchester.cs.owl.owlapi.OWLDataAllValuesFromImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLDataCardinalityRestrictionImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLDataHasValueImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLDataSomeValuesFromImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectCardinalityRestrictionImpl;
import aml.AML;
import aml.settings.LexicalType;
import aml.settings.EntityType;
import aml.util.StringParser;
import aml.util.Table2List;
import aml.util.Table2Map;

public class Ontology
{

//Attributes
	
	//The OWL Ontology Manager and Data Factory
	private OWLOntologyManager manager;
	private OWLDataFactory factory;
	//The entity expansion limit property
    private final String LIMIT = "entityExpansionLimit"; 
	//The URI of the ontology
	protected String uri;
	//The EntityType -> numeric index (Integer) map of ontology entities 
	private Table2List<EntityType,Integer> typeIndexes;
	//The map of indexes (Integer) -> Entities in the ontology
	protected HashMap<Integer,Entity> entities;
	//The map of class names (String) -> indexes (Integer) in the ontology
	protected HashMap<String,Integer> classNames; //This is necessary for handling cross-references
	//Its lexicon
	protected Lexicon lex;
	//Its word lexicon
	protected WordLexicon wLex;
	//Its map of cross-references
	protected ReferenceMap refs;
	//Its set of obsolete classes
	protected HashSet<Integer> obsolete;
	
	//Global variables data structures
	private AML aml;
	private boolean useReasoner;
	protected URIMap uris;
	protected RelationshipMap rm;
	
	//Auxiliary data structures to capture semantic disjointness
	private Table2Map<Integer,Integer,Integer> maxCard, minCard, card;
	private Table2Map<Integer,Integer,String> dataAllValues, dataHasValue, dataSomeValues;
	private Table2Map<Integer,Integer,Integer> objectAllValues, objectSomeValues;
	
//Constructors

	/**
	 * Constructs an empty ontology
	 */
	public Ontology()
	{
		//Initialize the data structures
		typeIndexes = new Table2List<EntityType,Integer>();
		entities = new HashMap<Integer,Entity>();
		classNames = new HashMap<String,Integer>();
		lex = new Lexicon();
		wLex = null;
		refs = new ReferenceMap();
		aml = AML.getInstance();
		useReasoner = aml.useReasoner();
		uris = aml.getURIMap();
		rm = aml.getRelationshipMap();
		obsolete = new HashSet<Integer>();
	}
	
	/**
	 * Constructs an Ontology from file 
	 * @param path: the path to the input Ontology file
	 * @param isInput: whether the ontology is an input ontology or an external ontology
	 * @throws OWLOntologyCreationException 
	 */
	public Ontology(String path, boolean isInput) throws OWLOntologyCreationException
	{
		this();
        //Increase the entity expansion limit to allow large ontologies
        System.setProperty(LIMIT, "1000000");
        //Get an Ontology Manager and Data Factory
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        //Load the local ontology
        File f = new File(path);
        OWLOntology o;
		o = manager.loadOntologyFromOntologyDocument(f);
		uri = f.getAbsolutePath();
		init(o,isInput);
		//Close the OntModel
        manager.removeOntology(o);
        //Reset the entity expansion limit
        System.clearProperty(LIMIT);
	}
	
	/**
	 * Constructs an Ontology from an URI  
	 * @param uri: the URI of the input Ontology
	 * @param isInput: whether the ontology is an input ontology or
	 * an external ontology
	 * @throws OWLOntologyCreationException 
	 */
	public Ontology(URI uri, boolean isInput) throws OWLOntologyCreationException
	{
		this();
        //Increase the entity expansion limit to allow large ontologies
        System.setProperty(LIMIT, "1000000");
        //Get an Ontology Manager and Data Factory
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        OWLOntology o;
        //Check if the URI is local
        if(uri.toString().startsWith("file:"))
		{
			File f = new File(uri);
			o = manager.loadOntologyFromOntologyDocument(f);
		}
		else
		{
			IRI i = IRI.create(uri);
			o = manager.loadOntology(i);
		}
        this.uri = uri.toString(); 
		init(o,isInput);
		//Close the OntModel
        manager.removeOntology(o);
        //Reset the entity expansion limit
        System.clearProperty(LIMIT);
	}
	
	/**
	 * Constructs an Ontology from an OWLOntology
	 * @param o: the OWLOntology to use
	 * @param isInput: whether the ontology is an input ontology or
	 * an external ontology
	 */
	public Ontology(OWLOntology o, boolean isInput)
	{
		this();
		init(o,isInput);
	}

//Public Methods

	/**
	 * @return the number of Classes in the Ontology
	 */
	public int classCount()
	{
		return entityCount(EntityType.CLASS);
	}
	
	/**
	 * Erases the Ontology data structures
	 */
	public void close()
	{
		uri = null;
		typeIndexes = null;
		entities = null;
		lex = null;
		refs = null;
	}
	
	/**
	 * @param index: the index of the entity in the ontology
	 * @return whether the entity belongs to the ontology
	 */
	public boolean contains(int index)
	{
		return entities.containsKey(index);
	}
	
	/**
	 * @param e: the type of the entity in the ontology
	 * @return whether the ontology contains entities of the given type
	 */
	public boolean contains(EntityType e)
	{
		return typeIndexes.contains(e);
	}
	
	/**
	 * @param type: the EntityType to get from the Ontology
	 * @return the number of entities of the given type in the Ontology
	 */
	public int entityCount(EntityType type)
	{
		if(typeIndexes.contains(type))
			return typeIndexes.get(type).size();
		return 0;
	}
	
	/**
	 * @param index: the index of the entity to get
	 * @return the Entity of the corresponding index in the Ontology
	 */
	public Entity getEntity(int index)
	{
		return entities.get(index);
	}
	
	/**
	 * @param name: the localName of the class to get from the Ontology
	 * @return the index of the corresponding name in the Ontology
	 */
	public int getIndex(String name)
	{
		return classNames.get(name);
	}	
	
	/**
	 * @param type: the EntityType to get from the Ontology
	 * @return the indexes of the corresponding type in the Ontology
	 */
	public Vector<Integer> getIndexes(EntityType type)
	{
		if(typeIndexes.contains(type))
			return typeIndexes.get(type);
		return new Vector<Integer>();
	}	
	
	/**
	 * @return the Lexicon of the Ontology
	 */
	public Lexicon getLexicon()
	{
		return lex;
	}
	
	/**
	 * @return the set of class local names in the Ontology
	 */
	public Set<String> getLocalNames()
	{
		return classNames.keySet();
	}
	
	/**
	 * @param index: the index of the entity to get the name
	 * @return the local name of the entity with the given index
	 */
	public String getLocalName(int index)
	{
		return entities.get(index).getName();
	}
	
	/**
	 * @param index: the index of the term/property to get the name
	 * @return the primary name of the term/property with the given index
	 */
	public String getName(int index)
	{
		return getLexicon().getBestName(index);
	}

	/**
	 * @return the ReferenceMap of the Ontology
	 */
	public ReferenceMap getReferenceMap()
	{
		return refs;
	}
	
	/**
	 * @return the types of entities contained in this Ontology
	 */
	public Set<EntityType> getTypes()
	{
		return typeIndexes.keySet();
	}
	
	/**
	 * @return the URI of the Ontology
	 */
	public String getURI()
	{
		return uri;
	}
	
	/**
	 * Gets the WordLexicon of this Ontology.
	 * Builds the WordLexicon if not previously built, or
	 * built for a specific language
	 * @return the WordLexicon of this Ontology
	 */
	public WordLexicon getWordLexicon()
	{
		if(wLex == null || !wLex.getLanguage().equals(""))
			wLex = new WordLexicon(lex);
		return wLex;
	}
	
	/**
	 * Gets the WordLexicon of this Ontology for the
	 * specified language.
	 * Builds the WordLexicon if not previously built, or
	 * built for a different or unspecified language.
	 * @param lang: the language of the WordLexicon
	 * @return the WordLexicon of this Ontology
	 */
	public WordLexicon getWordLexicon(String lang)
	{
		if(wLex == null || !wLex.getLanguage().equals(lang))
			wLex = new WordLexicon(lex,lang);
		return wLex;
	}
	
	/**
	 * @param index: the index of the URI in the ontology
	 * @return whether the index corresponds to an obsolete class
	 */
	public boolean isObsoleteClass(int index)
	{
		return obsolete.contains(index);
	}
	
	/**
	 * @return the number of Properties in the Ontology
	 */
	public int propertyCount()
	{
		return entityCount(EntityType.ANNOTATION) +
				entityCount(EntityType.DATA) +
				entityCount(EntityType.OBJECT);
	}
	
//Private Methods	

	//Builds the ontology data structures
	private void init(OWLOntology o, boolean isInput)
	{
		//Update the URI of the ontology (if it lists one)
		if(o.getOntologyID().getOntologyIRI() != null)
			uri = o.getOntologyID().getOntologyIRI().toString();
		//Get the classes and their names and synonyms
		getClasses(o);
		//Get the properties
		getProperties(o,isInput);
		//Extend the Lexicon
		lex.generateStopWordSynonyms();
		lex.generateParenthesisSynonyms();
		//Build the relationship map
		if(isInput)
			getRelationships(o);
	}
	
	//Processes the classes, their lexical information and cross-references
	private void getClasses(OWLOntology o)
	{
		//The Lexical type and weight
		LexicalType type;
		double weight;
		//The label property
		OWLAnnotationProperty label = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		//Get an iterator over the ontology classes
		Set<OWLClass> classes = o.getClassesInSignature(true);
		//Then get the URI for each class
		for(OWLClass c : classes)
		{
			String classUri = c.getIRI().toString();
			if(classUri == null || classUri.endsWith("owl#Thing") || classUri.endsWith("owl:Thing"))
				continue;
			//Add it to the global list of URIs
			int id = uris.addURI(classUri,EntityType.CLASS);
			//Add it to the maps of the corresponding type
			typeIndexes.add(EntityType.CLASS,id);
			
			//Get the local name from the URI
			String name = getLocalName(classUri);
			//Create the entity and add it to the entities map
			entities.put(id, new Entity(name));
			//Also add the name to the classNames map
			classNames.put(name, id);
			
			//If the local name is not an alphanumeric code, add it to the lexicon
			if(!StringParser.isNumericId(name))
			{
				type = LexicalType.LOCAL_NAME;
				weight = type.getDefaultWeight();
				lex.addClass(id, name, "en", type, "", weight);
			}

			//Now get the class's annotations (including imports)
			Set<OWLAnnotation> annots = c.getAnnotations(o);
			for(OWLOntology ont : o.getImports())
				annots.addAll(c.getAnnotations(ont));
            for(OWLAnnotation annotation : annots)
            {
            	//Labels and synonyms go to the Lexicon
            	String propUri = annotation.getProperty().getIRI().toString();
            	type = LexicalType.getLexicalType(propUri);
            	if(type != null)
            	{
	            	weight = type.getDefaultWeight();
	            	if(annotation.getValue() instanceof OWLLiteral)
	            	{
	            		OWLLiteral val = (OWLLiteral) annotation.getValue();
	            		name = val.getLiteral();
	            		String lang = val.getLang();
	            		if(lang.equals(""))
	            			lang = "en";
	            		lex.addClass(id, name, lang, type, "", weight);
		            }
	            	else if(annotation.getValue() instanceof IRI)
	            	{
	            		OWLNamedIndividual ni = factory.getOWLNamedIndividual((IRI) annotation.getValue());
	                    for(OWLAnnotation a : ni.getAnnotations(o,label))
	                    {
	                       	if(a.getValue() instanceof OWLLiteral)
	                       	{
	                       		OWLLiteral val = (OWLLiteral) a.getValue();
	                       		name = val.getLiteral();
	                       		String lang = val.getLang();
	    	            		if(lang.equals(""))
	    	            			lang = "en";
    		            		lex.addClass(id, name, lang, type, "", weight);
	                       	}
	            		}
	            	}
            	}
            	//xRefs go to the ReferenceMap
            	else if(propUri.endsWith("hasDbXref") &&
            			annotation.getValue() instanceof OWLLiteral)
            	{
            		OWLLiteral val = (OWLLiteral) annotation.getValue();
					String xRef = val.getLiteral();
					if(!xRef.startsWith("http"))
						refs.add(id,xRef.replace(':','_'));
            	}
            	//Deprecated classes are flagged as obsolete
            	else if(propUri.endsWith("deprecated") &&
            			annotation.getValue() instanceof OWLLiteral)
            	{
            		OWLLiteral val = (OWLLiteral) annotation.getValue();
            		if(val.isBoolean())
            		{
            			boolean deprecated = val.parseBoolean();
            			if(deprecated)
            				obsolete.add(id);
            		}
            	}
	        }
		}
	}
	
	//Reads the properties
	private void getProperties(OWLOntology o, boolean isInput)
	{
		LexicalType type;
		double weight;
		//The label property
		OWLAnnotationProperty label = factory
                .getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		//Get the Annotation Properties
		Set<OWLAnnotationProperty> anProps = o.getAnnotationPropertiesInSignature();
    	for(OWLAnnotationProperty ap : anProps)
    	{
    		String propUri = ap.getIRI().toString();
    		if(propUri == null)
    			continue;
			//Add it to the global list of URIs
			int id = uris.addURI(propUri,EntityType.ANNOTATION);
			
			//Add it to the maps of the corresponding type
			typeIndexes.add(EntityType.ANNOTATION,id);
			
			//Get the local name from the URI
			String name = getLocalName(propUri);
			//Create the entity and add it to the entities map
			entities.put(id, new Entity(name));
			
			//If the local name is not an alphanumeric code, add it to the lexicon
			if(!StringParser.isNumericId(name))
			{
				type = LexicalType.LOCAL_NAME;
				weight = type.getDefaultWeight();
				lex.addProperty(id, name, "en", type, "", weight);
			}
			
			for(OWLAnnotation a : ap.getAnnotations(o,label))
            {
				String lang = "";
               	if(a.getValue() instanceof OWLLiteral)
               	{
               		OWLLiteral val = (OWLLiteral) a.getValue();
               		name = val.getLiteral();
               		lang = val.getLang();
            		if(lang.equals(""))
            			lang = "en";
    				type = LexicalType.LABEL;
    				weight = type.getDefaultWeight();
    				lex.addProperty(id, name, "en", type, "", weight);
               	}
            }
    	}
		//Get the Data Properties
		Set<OWLDataProperty> dProps = o.getDataPropertiesInSignature(true);
    	for(OWLDataProperty dp : dProps)
    	{
    		String propUri = dp.getIRI().toString();
    		if(propUri == null)
    			continue;
 			//Add it to the global list of URIs
			int id = uris.addURI(propUri,EntityType.DATA);
			//Add it to the maps of the corresponding type
			typeIndexes.add(EntityType.DATA,id);
			
			//Get the local name from the URI
			String name = getLocalName(propUri);
			
			//If the local name is not an alphanumeric code, add it to the lexicon
			if(!StringParser.isNumericId(name))
			{
				type = LexicalType.LOCAL_NAME;
				weight = type.getDefaultWeight();
				lex.addProperty(id, name, "en", type, "", weight);
			}
			
			for(OWLAnnotation a : dp.getAnnotations(o,label))
            {
				String lang = "";
               	if(a.getValue() instanceof OWLLiteral)
               	{
               		OWLLiteral val = (OWLLiteral) a.getValue();
               		name = val.getLiteral();
               		lang = val.getLang();
            		if(lang.equals(""))
            			lang = "en";
    				type = LexicalType.LABEL;
    				weight = type.getDefaultWeight();
    				lex.addProperty(id, name, "en", type, "", weight);
               	}
            }
			//Initialize the property
			DataProperty prop = new DataProperty(name,dp.isFunctional(o));
			//Get its domain
			Set<OWLClassExpression> domains = dp.getDomains(o);
			for(OWLClassExpression ce : domains)
			{
				Set<OWLClassExpression> union = ce.asDisjunctSet();
				for(OWLClassExpression cf : union)
				{
					if(cf instanceof OWLClass)
						prop.addDomain(uris.getIndex(cf.asOWLClass().getIRI().toString()));
				}
			}
			//And finally its range(s)
			Set<OWLDataRange> ranges = dp.getRanges(o);
			for(OWLDataRange dr : ranges)
			{
				if(dr.isDatatype())
					prop.addRange(dr.asOWLDatatype().toStringID());
			}
			entities.put(id, prop);
    	}
		//Get the Object Properties
		Set<OWLObjectProperty> oProps = o.getObjectPropertiesInSignature(true);
    	for(OWLObjectProperty op : oProps)
    	{
    		String propUri = op.getIRI().toString();
    		if(propUri == null)
    			continue;
			//Add it to the global list of URIs
			int id = uris.addURI(propUri,EntityType.OBJECT);
			//It is transitive, add it to the RelationshipMap
			if(op.isTransitive(o))
				aml.getRelationshipMap().setTransitive(id);
			//Add it to the maps of the corresponding type
			typeIndexes.add(EntityType.OBJECT,id);
			
			//Get the local name from the URI
			String name = getLocalName(propUri);
			
			//If the local name is not an alphanumeric code, add it to the lexicon
			if(!StringParser.isNumericId(name))
			{
				type = LexicalType.LOCAL_NAME;
				weight = type.getDefaultWeight();
				lex.addProperty(id, name, "en", type, "", weight);
			}
			
			for(OWLAnnotation a : op.getAnnotations(o,label))
            {
				String lang = "";
               	if(a.getValue() instanceof OWLLiteral)
               	{
               		OWLLiteral val = (OWLLiteral) a.getValue();
               		name = val.getLiteral();
               		lang = val.getLang();
            		if(lang.equals(""))
            			lang = "en";
    				type = LexicalType.LABEL;
    				weight = type.getDefaultWeight();
    				lex.addProperty(id, name, "en", type, "", weight);
               	}
            }
			//Initialize the property
			ObjectProperty prop = new ObjectProperty(name,op.isFunctional(o));
			//Get its domain
			Set<OWLClassExpression> domains = op.getDomains(o);
			for(OWLClassExpression ce : domains)
			{
				Set<OWLClassExpression> union = ce.asDisjunctSet();
				for(OWLClassExpression cf : union)
				{
					if(cf instanceof OWLClass)
						prop.addDomain(uris.getIndex(cf.asOWLClass().getIRI().toString()));
				}
			}
			//And finally its range(s)
			Set<OWLClassExpression> ranges = op.getRanges(o);
			for(OWLClassExpression ce : ranges)
			{
				Set<OWLClassExpression> union = ce.asDisjunctSet();
				for(OWLClassExpression cf : union)
				{
					if(cf instanceof OWLClass)
						prop.addRange(uris.getIndex(cf.asOWLClass().getIRI().toString()));
				}
			}
			entities.put(id, prop);
    	}
	}

	//Reads all class relationships
	private void getRelationships(OWLOntology o)
	{
		OWLReasoner reasoner = null;		
		if(useReasoner)
		{
			//Create an ELK reasoner
			OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			reasoner = reasonerFactory.createReasoner(o);
		}
		
		//Auxiliary data structures to capture semantic disjointness
		//Two classes are disjoint if they have:
		//1) Incompatible cardinality restrictions for the same property
		maxCard = new Table2Map<Integer,Integer,Integer>();
		minCard = new Table2Map<Integer,Integer,Integer>();
		card = new Table2Map<Integer,Integer,Integer>();
		//2) Different values for the same functional data property or incompatible value
		//restrictions on the same non-functional data property
		dataAllValues = new Table2Map<Integer,Integer,String>();
		dataHasValue = new Table2Map<Integer,Integer,String>();
		dataSomeValues = new Table2Map<Integer,Integer,String>();
		//3) Disjoint classes for the same functional object property or incompatible value
		//restrictions on disjoint classes for the same non-functional object property
		objectAllValues = new Table2Map<Integer,Integer,Integer>();
		objectSomeValues = new Table2Map<Integer,Integer,Integer>();
		
		//Get an iterator over the ontology classes
		Set<OWLClass> classes = o.getClassesInSignature(true);
		//For each term index (from 'termURIs' list)
		for(OWLClass c : classes)
		{
			//Get the identifier of the child
			int child = uris.getIndex(c.getIRI().toString());
			int parent;
			if(child == -1)
				continue;
			
			if(useReasoner)
			{
				//Get its superclasses using the reasoner
				Set<OWLClass> parents = reasoner.getSuperClasses(c, true).getFlattened();
				for(OWLClass par : parents)
				{
					parent = uris.getIndex(par.getIRI().toString());
					if(parent > -1)
					{
						rm.addDirectSubclass(child, parent);
						String name = getLocalName(parent);
						if(name.contains("Obsolete") || name.contains("obsolete") ||
								name.contains("Retired") || name.contains ("retired") ||
								name.contains("Deprecated") || name.contains("deprecated"))
							obsolete.add(child);
					}
				}
				//Get its equivalent classes using the reasoner
				Node<OWLClass> equivs = reasoner.getEquivalentClasses(c);
				for(OWLClass eq : equivs)
				{
					parent = uris.getIndex(eq.getIRI().toString());
					if(parent > -1 && parent != child)
						rm.addEquivalentClass(child, parent);
				}
			}
			
			//Get the subclass expressions to capture and add relationships
			Set<OWLClassExpression> superClasses = c.getSuperClasses(o);
			for(OWLOntology ont : o.getDirectImports())
				superClasses.addAll(c.getSuperClasses(ont));
			for(OWLClassExpression e : superClasses)
				addRelationship(o,c,e,true,false);
			
			//Get the equivalence expressions to capture and add relationships
			Set<OWLClassExpression> equivClasses = c.getEquivalentClasses(o);
			for(OWLOntology ont : o.getDirectImports())
				equivClasses.addAll(c.getEquivalentClasses(ont));
			for(OWLClassExpression e : equivClasses)
				addRelationship(o,c,e,false,false);
			
			//Get the syntactic disjoints
			Set<OWLClassExpression> disjClasses = c.getDisjointClasses(o);
			for(OWLOntology ont : o.getDirectImports())
				disjClasses.addAll(c.getDisjointClasses(ont));
			//For each expression
			for(OWLClassExpression dClass : disjClasses)
			{
				ClassExpressionType type = dClass.getClassExpressionType();
				//If it is a class, process it
				if(type.equals(ClassExpressionType.OWL_CLASS))
				{
					parent = uris.getIndex(dClass.asOWLClass().getIRI().toString());
					if(parent > -1)
						rm.addDisjoint(child, parent);
				}
				//If it is a union, process it
				if(type.equals(ClassExpressionType.OBJECT_UNION_OF))
				{
					Set<OWLClass> dc = dClass.getClassesInSignature();
					for(OWLClass ce : dc)
					{
						parent = uris.getIndex(ce.getIRI().toString());
						if(parent > -1)
							rm.addDisjoint(child, parent);
					}
				}
				//If it is an intersection, check for common descendants
				if(type.equals(ClassExpressionType.OBJECT_INTERSECTION_OF))
				{
					Set<OWLClass> dc = dClass.getClassesInSignature();
					HashSet<Integer> intersect = new HashSet<Integer>();
					for(OWLClass ce : dc)
					{
						parent = uris.getIndex(ce.getIRI().toString());
						if(parent > -1)
							intersect.add(parent);
					}
					Set<Integer> subclasses = rm.getCommonSubClasses(intersect);
					for(Integer i : subclasses)
						rm.addDisjoint(child,i);
				}
			}
		}
		
		//Finally process the semantically disjoint classes
		//Classes that have incompatible cardinalities on the same property
		//First exact cardinalities vs exact, min and max cardinalities
		for(Integer prop : card.keySet())
		{
			Vector<Integer> exact = new Vector<Integer>(card.keySet(prop));
			for(int i = 0; i < exact.size()-1; i++)
				for(int j = i+1; j < exact.size(); j++)
					if(card.get(prop, exact.get(i)) != card.get(prop, exact.get(j)))
						rm.addDisjoint(exact.get(i), exact.get(j));
			Set<Integer> max = maxCard.keySet(prop);
			if(max != null)
				for(int i = 0; i < exact.size(); i++)
					for(Integer j : max)
						if(card.get(prop, exact.get(i)) > maxCard.get(prop, j))
							rm.addDisjoint(exact.get(i), j);
			Set<Integer> min = minCard.keySet(prop);
			if(min != null)
				for(int i = 0; i < exact.size(); i++)
					for(Integer j : min)
						if(card.get(prop, exact.get(i)) > minCard.get(prop, j))
							rm.addDisjoint(exact.get(i), j);				
		}
		//Then min vs max cardinalities
		for(Integer prop : minCard.keySet())
		{
			Set<Integer> min = minCard.keySet(prop);
			Set<Integer> max = maxCard.keySet(prop);
			if(max == null)
				continue;
			for(Integer i : min)
				for(Integer j : max)
					if(minCard.get(prop, i) > maxCard.get(prop, j))
						rm.addDisjoint(i, j);
		}
		//Data properties with incompatible values
		//First hasValue restrictions on functional data properties
		for(Integer prop : dataHasValue.keySet())
		{
			Vector<Integer> cl = new Vector<Integer>(dataHasValue.keySet(prop));
			for(int i = 0; i < cl.size()-1; i++)
				for(int j = i+1; j < cl.size(); j++)
					if(!dataHasValue.get(prop, cl.get(i)).equals(dataHasValue.get(prop, cl.get(j))))
						rm.addDisjoint(cl.get(i), cl.get(j));
		}
		//Then incompatible someValues restrictions on functional data properties
		for(Integer prop : dataSomeValues.keySet())
		{
			Vector<Integer> cl = new Vector<Integer>(dataSomeValues.keySet(prop));
			for(int i = 0; i < cl.size()-1; i++)
			{
				for(int j = i+1; j < cl.size(); j++)
				{
					String[] datatypes = dataSomeValues.get(prop, cl.get(j)).split(" ");
					for(String d: datatypes)
					{
						if(!dataSomeValues.get(prop, cl.get(i)).contains(d))
						{
							rm.addDisjoint(cl.get(i), cl.get(j));
							break;
						}
					}
				}
			}
		}
		//Then incompatible allValues restrictions on all data properties
		//(allValues vs allValues and allValues vs someValues)
		for(Integer prop : dataAllValues.keySet())
		{
			Vector<Integer> cl = new Vector<Integer>(dataAllValues.keySet(prop));
			for(int i = 0; i < cl.size()-1; i++)
			{
				for(int j = i+1; j < cl.size(); j++)
				{
					String[] datatypes = dataAllValues.get(prop, cl.get(j)).split(" ");
					for(String d: datatypes)
					{
						if(!dataAllValues.get(prop, cl.get(i)).contains(d))
						{
							rm.addDisjoint(cl.get(i), cl.get(j));
							break;
						}
					}
				}
			}
			Set<Integer> sv = dataSomeValues.keySet(prop);
			if(sv == null)
				continue;
			for(Integer i : cl)
			{
				for(Integer j : sv)
				{
					String[] datatypes = dataSomeValues.get(prop, j).split(" ");
					for(String d: datatypes)
					{
						if(!dataAllValues.get(prop, i).contains(d))
						{
							rm.addDisjoint(i, j);
							break;
						}
					}
				}
			}
		}
		//Classes that incompatible value restrictions for the same object property
		//(i.e., the restrictions point to disjoint classes)
		//First allValues restrictions
		for(Integer prop : objectAllValues.keySet())
		{
			Vector<Integer> cl = new Vector<Integer>(objectAllValues.keySet(prop));
			for(int i = 0; i < cl.size() - 1; i++)
			{
				int c1 = objectAllValues.get(prop, cl.get(i));
				for(int j = i + 1; j < cl.size(); j++)
				{
					int c2 = objectAllValues.get(prop, cl.get(j));
					if(c1 != c2 && rm.areDisjoint(c1, c2))
						rm.addDisjoint(cl.get(i), cl.get(j));
				}
			}
		
			Set<Integer> sv = objectSomeValues.keySet(prop);
			if(sv == null)
				continue;
			for(Integer i : cl)
			{
				int c1 = objectAllValues.get(prop, i);
				for(Integer j : sv)
				{
					int c2 = objectSomeValues.get(prop, j);
					if(c1 != c2 && rm.areDisjoint(c1, c2))
						rm.addDisjoint(i, j);
				}
			}
		}
		//Finally someValues restrictions on functional properties
		for(Integer prop : objectAllValues.keySet())
		{
			if(!((ObjectProperty)entities.get(prop)).isFunctional())
				continue;
			Set<Integer> sv = objectSomeValues.keySet(prop);
			if(sv == null)
				continue;
			Vector<Integer> cl = new Vector<Integer>(sv);
			
			for(int i = 0; i < cl.size() - 1; i++)
			{
				int c1 = objectSomeValues.get(prop, cl.get(i));
				for(int j = i + 1; j < cl.size(); j++)
				{
					int c2 = objectAllValues.get(prop, cl.get(j));
					if(c1 != c2 && rm.areDisjoint(c1, c2))
						rm.addDisjoint(cl.get(i), cl.get(j));
				}
			}
		}
		
		//Clean auxiliary data structures
		maxCard = null;
		minCard = null;
		card = null;
		dataAllValues = null;
		dataHasValue = null;
		dataSomeValues = null;
		objectAllValues = null;
		objectSomeValues = null;
		
		//Finally process relationships between properties
		//Data Properties
		Set<OWLDataProperty> dProps = o.getDataPropertiesInSignature(true);
    	for(OWLDataProperty dp : dProps)
    	{
    		int propId = uris.getIndex(dp.getIRI().toString());
    		if(propId == -1)
    			continue;
    		Set<OWLDataPropertyExpression> sProps = dp.getSuperProperties(o);
			for(OWLDataPropertyExpression de : sProps)
			{
				OWLDataProperty sProp = de.asOWLDataProperty();
				int sId = uris.getIndex(sProp.getIRI().toString());
				if(sId != -1)
					rm.addPropertyRel(propId,sId);	
			}
    	}
		//Object Properties
		Set<OWLObjectProperty> oProps = o.getObjectPropertiesInSignature(true);
    	for(OWLObjectProperty op : oProps)
    	{
    		int propId = uris.getIndex(op.getIRI().toString());
    		if(propId == -1)
    			continue;
    		Set<OWLObjectPropertyExpression> sProps = op.getSuperProperties(o);
			for(OWLObjectPropertyExpression oe : sProps)
			{
				OWLObjectProperty sProp = oe.asOWLObjectProperty();
				int sId = uris.getIndex(sProp.getIRI().toString());
				if(sId != -1)
					rm.addPropertyRel(propId,sId);	
			}
    		Set<OWLObjectPropertyExpression> iProps = op.getInverses(o);
			for(OWLObjectPropertyExpression oe : iProps)
			{
				OWLObjectProperty iProp = oe.asOWLObjectProperty();
				int iId = uris.getIndex(iProp.getIRI().toString());
				if(iId != -1)
					rm.addInverseProp(propId,iId);	
			}
    	}
	}
	
	//Get the local name of an entity from its URI
	private String getLocalName(String uri)
	{
		int index = uri.indexOf("#") + 1;
		if(index == 0)
			index = uri.lastIndexOf("/") + 1;
		return uri.substring(index);
	}
	
	private void addRelationship(OWLOntology o, OWLClass c, OWLClassExpression e, boolean sub, boolean inverse)
	{
		int child = uris.getIndex(c.getIRI().toString());
		int parent;
		ClassExpressionType type = e.getClassExpressionType();
		//If it is a class, and we didn't use the reasoner, process it here
		if(type.equals(ClassExpressionType.OWL_CLASS))
		{
			parent = uris.getIndex(e.asOWLClass().getIRI().toString());
			if(parent < 0)
				return;
			if(sub)
			{
				if(inverse)
					rm.addDirectSubclass(parent, child);
				else
					rm.addDirectSubclass(child, parent);
				String name = getName(parent);
				if(name.contains("Obsolete") || name.contains("obsolete") ||
						name.contains("Retired") || name.contains ("retired") ||
						name.contains("Deprecated") || name.contains("deprecated"))
					obsolete.add(child);
			}
			else
				rm.addEquivalentClass(child, parent);
		}
		//If it is a 'some values' object property restriction, process it
		else if(type.equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM))
		{
			Set<OWLObjectProperty> props = e.getObjectPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLObjectProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			Set<OWLClass> sup = e.getClassesInSignature();
			if(sup == null || sup.size() != 1)
				return;					
			OWLClass cls = sup.iterator().next();
			parent = uris.getIndex(cls.getIRI().toString());
			if(parent == -1 || property == -1)
				return;
			if(sub)
			{
				if(inverse)
					rm.addDirectRelationship(parent, child, property, false);
				else
					rm.addDirectRelationship(child, parent, property, false);
			}
			else
				rm.addEquivalence(child, parent, property, false);
			objectSomeValues.add(property, child, parent);
		}
		//If it is a 'all values' object property restriction, process it
		else if(type.equals(ClassExpressionType.OBJECT_ALL_VALUES_FROM))
		{
			Set<OWLObjectProperty> props = e.getObjectPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLObjectProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			Set<OWLClass> sup = e.getClassesInSignature();
			if(sup == null || sup.size() != 1)
				return;					
			OWLClass cls = sup.iterator().next();
			parent = uris.getIndex(cls.getIRI().toString());
			if(parent == -1 || property == -1)
				return;
			if(sub)
			{
				if(inverse)
					rm.addDirectRelationship(parent, child, property, false);
				else
					rm.addDirectRelationship(child, parent, property, false);
			}
			else
				rm.addEquivalence(child, parent, property, false);

			objectAllValues.add(property, child, parent);
		}
		//If it is an intersection of classes, capture the implied subclass relationships
		else if(type.equals(ClassExpressionType.OBJECT_INTERSECTION_OF))
		{
			Set<OWLClassExpression> inter = e.asConjunctSet();
			for(OWLClassExpression cls : inter)
				addRelationship(o,c,cls,true,false);
		}
		//If it is a union of classes, capture the implied subclass relationships
		else if(type.equals(ClassExpressionType.OBJECT_UNION_OF))
		{
			Set<OWLClassExpression> union = e.asDisjunctSet();
			for(OWLClassExpression cls : union)
				addRelationship(o,c,cls,true,true);
		}
		//Otherwise, we're only interested in properties that may lead to disjointness
		else if(type.equals(ClassExpressionType.OBJECT_EXACT_CARDINALITY))
		{
			Set<OWLObjectProperty> props = e.getObjectPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLObjectProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			int cardinality = ((OWLObjectCardinalityRestrictionImpl)e).getCardinality();
			card.add(property, child, cardinality);					
		}
		else if(type.equals(ClassExpressionType.OBJECT_MAX_CARDINALITY))
		{
			Set<OWLObjectProperty> props = e.getObjectPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLObjectProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			int cardinality = ((OWLObjectCardinalityRestrictionImpl)e).getCardinality();
			maxCard.add(property, child, cardinality);					
		}
		else if(type.equals(ClassExpressionType.OBJECT_MIN_CARDINALITY))
		{
			Set<OWLObjectProperty> props = e.getObjectPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLObjectProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			int cardinality = ((OWLObjectCardinalityRestrictionImpl)e).getCardinality();
			minCard.add(property, child, cardinality);					
		}
		else if(type.equals(ClassExpressionType.DATA_ALL_VALUES_FROM))
		{
			OWLDataAllValuesFromImpl av = (OWLDataAllValuesFromImpl)e;
			Set<OWLDataProperty> props = av.getDataPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLDataProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			Set<OWLDatatype> dt = av.getDatatypesInSignature();
			String value = "";
			for(OWLDatatype d : dt)
				value += d.toString() + " ";
			value.trim();
			dataAllValues.add(property, child, value);
		}
		else if(type.equals(ClassExpressionType.DATA_SOME_VALUES_FROM))
		{
			OWLDataSomeValuesFromImpl av = (OWLDataSomeValuesFromImpl)e;
			Set<OWLDataProperty> props = av.getDataPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLDataProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			Set<OWLDatatype> dt = av.getDatatypesInSignature();
			String value = "";
			for(OWLDatatype d : dt)
				value += d.toString() + " ";
			value.trim();
			dataSomeValues.add(property, child, value);
		}
		else if(type.equals(ClassExpressionType.DATA_HAS_VALUE))
		{
			OWLDataHasValueImpl hv = (OWLDataHasValueImpl)e; 
			Set<OWLDataProperty> props = hv.getDataPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLDataProperty p = props.iterator().next();
			if(!p.isFunctional(o))
				return;
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			String value = hv.getValue().toString();
			if(p.isFunctional(o))
				dataHasValue.add(property, child, value);
		}
		else if(type.equals(ClassExpressionType.DATA_EXACT_CARDINALITY))
		{
			Set<OWLDataProperty> props = e.getDataPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLDataProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			int cardinality = ((OWLDataCardinalityRestrictionImpl)e).getCardinality();
			card.add(property, child, cardinality);					
		}
		else if(type.equals(ClassExpressionType.DATA_MAX_CARDINALITY))
		{
			Set<OWLDataProperty> props = e.getDataPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLDataProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			int cardinality = ((OWLDataCardinalityRestrictionImpl)e).getCardinality();
			maxCard.add(property, child, cardinality);					
		}
		else if(type.equals(ClassExpressionType.DATA_MIN_CARDINALITY))
		{
			Set<OWLDataProperty> props = e.getDataPropertiesInSignature();
			if(props == null || props.size() != 1)
				return;
			OWLDataProperty p = props.iterator().next();
			int property = uris.getIndex(p.getIRI().toString());
			if(property == -1)
				return;
			int cardinality = ((OWLDataCardinalityRestrictionImpl)e).getCardinality();
			minCard.add(property, child, cardinality);					
		}
	}
}