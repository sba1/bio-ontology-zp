package de.sonumina.zpgen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.coode.owlapi.obo.parser.OBOVocabulary;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;

/**
 * Main class for ZP which constructs an zebrafish phenotype ontology from
 * decomposed phenotype - gene associations.
 * 
 * The purpose of this tool is to create an ontology from the definition that
 * can be found in this file (source http://zfin.org/data_transfer/Downloads/phenotype.txt).
 * The input file is tab separated.
 *  
 * @author Sebastian Bauer
 * @author Sebastian Koehler
 */
public class ZPGen
{
	static boolean verbose = true;

	static Logger log = Logger.getLogger(ZPGen.class.getName());

	public static void main(String[] args) throws OWLOntologyCreationException, IOException, ParseException, InterruptedException
	{
		final CommandLineParser commandLineParser 	= new BasicParser();
		HelpFormatter formatter 					= new HelpFormatter();
		final Options options 						= new Options();
		
		Option zfinFileOpt = new Option("z", "zfin-file", true, "The ZFIN file (e.g., http://zfin.org/data_transfer/Downloads/phenotype.txt)");
		options.addOption(zfinFileOpt);
		Option ontoFileOpt = new Option("o","ontology-file", true, "Where the ontology file (e.g. ZP.owl) is written to.");
		options.addOption(ontoFileOpt);
		Option annotFileOpt = new Option("a", "annotation-file", true, "Where the annotation file (e.g. ZP.annot) is written to.");
		options.addOption(annotFileOpt);
		Option keepOpt = new Option("k", "keep-ids", false, "If the file on the output is already a valid ZP.owl file, keep the ids (ZP_nnnnnnn) stored in that file.");
		options.addOption(keepOpt);
		Option help = new Option( "h", "help",false, "Print this (help-)message.");
		options.addOption(help);
		
		final CommandLine commandLine 	= commandLineParser.parse(options, args);

		/*
		 * Check if user wants help how to use this program
		 */
		if (commandLine.hasOption(help.getOpt()) || commandLine.hasOption(help.getLongOpt()))
		{
			formatter.printHelp( ZPGen.class.getSimpleName() , options );
			return;
		}
		
		final String zfinFilePath  = getOption(zfinFileOpt, commandLine);
		final String ontoFilePath  = getOption(ontoFileOpt, commandLine);
		final String annotFilePath = getOption(annotFileOpt, commandLine);
		boolean keepIds = commandLine.hasOption(keepOpt.getOpt());
		
		// check that required parameters are set
		String parameterError = null;
		
		if (zfinFilePath == null) parameterError = "Required zfin-file is missing!";
		else if (ontoFilePath == null) parameterError = "Required option ontology-output-file is missing!";
		else if (annotFilePath == null) parameterError = "Required option annotation-output-file missing!";

		/*
		 * Maybe something was wrong with the parameter. Print help for 
		 * the user and die here...
		 */
		if (parameterError != null)
		{
			String className = ZPGen.class.getSimpleName();
			
			formatter.printHelp(className, options);
			throw new IllegalArgumentException(parameterError);
		}
		
		/* Create ontology manager and IRIs */
		final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		final IRI zpIRI = IRI.create("http://purl.obolibrary.org/obo/");

		/* Load the previous zp, if requested */
		final OWLOntology zp;
		if (keepIds)
		{
			File ontoFile = new File(ontoFilePath);
			if (ontoFile.exists())
			{
				zp = manager.loadOntologyFromOntologyDocument(ontoFile);
			} else
			{
				log.info("Ignoring non-existent file \""+ ontoFilePath + "\" for keeping the ids");
				zp = manager.createOntology(zpIRI);
			}
		} else
		{
			zp = manager.createOntology(zpIRI);
		}
		
		/* Instanciate the zpid db */
		final ZPIDDB zpIdDB = new ZPIDDB(zp);

		/* Where to write the annotation file to */
		final BufferedWriter annotationOut = new BufferedWriter(new FileWriter(annotFilePath));
		
		/* Obtain the default data factory */
		final OWLDataFactory factory = manager.getOWLDataFactory(); 
		
		/* Open zfin input file */
		File f = new File(zfinFilePath);
		if (f.isDirectory())
		{
			System.err.println(String.format("Specified zfin parameter value \"%s\" must point to a file (as the name suggests) and not a directory.",zfinFilePath));
			System.exit(-1);
		}
		
		if (!f.exists())
		{
			System.err.println(String.format("Specified file \"%s\" doesn't exist!",zfinFilePath));
			System.exit(-1);
		}
		
		File of = new File(ontoFilePath);

		final OWLObjectProperty towards 	= factory.getOWLObjectProperty(IRI.create(zpIRI + "BFO_0000070"));
		final OWLObjectProperty partOf 		= factory.getOWLObjectProperty(IRI.create(zpIRI + "BFO_0000050"));
		final OWLObjectProperty inheresIn	= factory.getOWLObjectProperty(IRI.create(zpIRI + "BFO_0000052"));
		final OWLObjectProperty hasPart		= factory.getOWLObjectProperty(IRI.create(zpIRI + "BFO_0000051"));
		
		// RO_0002180 = "qualifier"
		final OWLObjectProperty qualifier 	= factory.getOWLObjectProperty(IRI.create(zpIRI + "RO_0002180"));
		final OWLClass abnormal				= factory.getOWLClass(IRI.create(zpIRI + "PATO_0000460"));
		
		/* Now walk the file and create instances on the fly */
		try
		{
			InputStream is;

			/* Open input file. Try gzip compression first */
			try
			{
				is = new GZIPInputStream(new FileInputStream(f));
				if (is.markSupported())
					is.mark(10);
				is.read();
				if (is.markSupported())
					is.reset();
				else
					is = new GZIPInputStream(new FileInputStream(f));
			} catch(IOException ex)
			{
				/* We did not succeed in open the file and reading in a byte. We assume that
				 * the file is not compressed.
				 */
				is = new FileInputStream(f);
			}

			/* Constructs an OWLClass and Axioms for each zfin entry. We expect the reasoner to
			 * collate the classes properly. We also emit the annotations here. */
			ZFINWalker.walk(is, new ZFINVisitor()
			{
				/**
				 * Returns an entity class for the given obo id. This is a simple wrapper
				 * for OBOVocabulary.ID2IRI(id) but checks whether the term stems from
				 * a supported ontology.
				 * 
				 * @param id
				 * @return
				 */
				private OWLClass getEntityClassForOBOID(String id)
				{
					if (id.startsWith("GO:") || id.startsWith("ZFA:") || id.startsWith("BSPO:") || id.startsWith("MPATH:"))
						return factory.getOWLClass(OBOVocabulary.ID2IRI(id));

					throw new RuntimeException("Unknown ontology prefix for name \"" + id + "\"");
				}

				/**
				 * Returns an quality class for the given obo id.  This is a simple wrapper
				 * for OBOVocabulary.ID2IRI(id) but checks whether the term stems from
				 * a supported ontology.
				 * 
				 * @param id
				 * @return
				 */
				private OWLClass getQualiClassForOBOID(String id)
				{
					if (id.startsWith("PATO:")) return factory.getOWLClass(OBOVocabulary.ID2IRI(id));

					throw new RuntimeException("Qualifier must be a pato term");
				}

				public boolean visit(ZFINEntry entry)
				{
					// handle only abnormal entries
					if (! entry.isAbnormal)
						return true;

					// FIXME debug: exclude useless annotation that look like this
					// ...ZFA:0001439|anatomical system|||||||PATO:0000001|quality|abnormal|
					if ( entry.entity1SupertermId.equals("ZFA:0001439") &&
							entry.patoID.equals("PATO:0000001") &&
							entry.entity1SubtermId.equals("") &&
							entry.entity2SupertermId.equals("") &&
							entry.entity2SubtermId.equals(""))
						return true;
					
					
					OWLClass pato 	= getQualiClassForOBOID(entry.patoID);
					OWLClass cl1 	= getEntityClassForOBOID(entry.entity1SupertermId);
					OWLClassExpression intersectionExpression;
					String label;

					Set<OWLClassExpression> intersectionList = new LinkedHashSet<OWLClassExpression>();
					
					intersectionList.add(pato);
					intersectionList.add(factory.getOWLObjectSomeValuesFrom(qualifier, abnormal));
					
					/* Entity 1: Create intersections */
					if (entry.entity1SubtermId!= null && entry.entity1SubtermId.length() > 0)
					{
						/* Pattern is (all-some interpretation): <pato> inheres_in (<cl2> part of <cl1>) AND qualifier abnormal*/
						OWLClass cl2 = getEntityClassForOBOID(entry.entity1SubtermId);
						

						intersectionList.add(factory.getOWLObjectSomeValuesFrom(inheresIn, 
								factory.getOWLObjectIntersectionOf(cl2,factory.getOWLObjectSomeValuesFrom(partOf, cl1))));
						
						/* Note that is language the last word is the more specific part of the composition, i.e.,
						 * we say swim bladder epithelium, which is the epithelium of the swim bladder  */
						label = "abnormal(ly) " + entry.patoName +  " " + entry.entity1SupertermName + " " + entry.entity1SubtermName;
					} 
					else
					{
						/* Pattern is (all-some interpretation): <pato> inheres_in <cl1> AND qualifier abnormal */
						intersectionList.add(factory.getOWLObjectSomeValuesFrom(inheresIn, cl1));
						label = "abnormal(ly) " + entry.patoName +  " " + entry.entity1SupertermName;
					}
					
					
					/* Entity 2: Create intersections */
					if (entry.entity2SupertermId!= null && entry.entity2SupertermId.length() > 0){
						
						OWLClass cl3 = getEntityClassForOBOID(entry.entity2SupertermId);
						
						if (entry.entity2SubtermId!= null && entry.entity2SubtermId.length() > 0)
						{
							/* Pattern is (all-some interpretation): <pato> inheres_in (<cl2> part of <cl1>) AND qualifier abnormal*/
							OWLClass cl4 = getEntityClassForOBOID(entry.entity2SubtermId);
							
							intersectionList.add(factory.getOWLObjectSomeValuesFrom(towards, 
									factory.getOWLObjectIntersectionOf(cl4,factory.getOWLObjectSomeValuesFrom(partOf, cl3))));
							
							/* Note that is language the last word is the more specific part of the composition, i.e.,
							 * we say swim bladder epithelium, which is the epithelium of the swim bladder  */
							label += " towards " + entry.entity2SupertermName + " " + entry.entity2SubtermName;
							
						}
						else{
							intersectionList.add(factory.getOWLObjectSomeValuesFrom(towards, cl3));
							label += " towards " + entry.entity2SupertermName;
						}
					}
						
					/* Create intersection */
					intersectionExpression = factory.getOWLObjectIntersectionOf(intersectionList);
					
					OWLClassExpression owlSomeClassExp = factory.getOWLObjectSomeValuesFrom(hasPart,intersectionExpression);
					
					IRI zpIRI = zpIdDB.getZPId(owlSomeClassExp);
					String zpID = OBOVocabulary.IRI2ID(zpIRI);
					OWLClass zpTerm 	= factory.getOWLClass(zpIRI);

					/* Make term equivalent to the intersection */
					OWLEquivalentClassesAxiom axiom = factory.getOWLEquivalentClassesAxiom(zpTerm, owlSomeClassExp);
					manager.addAxiom(zp,axiom);

					/* Add label */
					OWLAnnotation labelAnno = factory.getOWLAnnotation(factory.getRDFSLabel(),factory.getOWLLiteral(label));
					OWLAxiom labelAnnoAxiom = factory.getOWLAnnotationAssertionAxiom(zpTerm.getIRI(), labelAnno);
					manager.addAxiom(zp,labelAnnoAxiom);

					/*
					 * Writing the annotation file
					 */
					try {
						annotationOut.write(entry.geneZfinID+"\t"+zpID+"\t"+label+"\n");
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					return true;
				}
			});

			manager.saveOntology(zp, new FileOutputStream(of));
			log.info("Wrote \"" + of.toString() + "\"");
			annotationOut.close();
		} 
		catch (FileNotFoundException e)
		{
			System.err.println(String.format("Specified input file \"%s\" doesn't exist!",zfinFilePath));
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		} 
		catch (OWLOntologyStorageException e) {
			e.printStackTrace();
		}
	}
	
	

	public static String getOption(Option opt, final CommandLine commandLine) {

		if (commandLine.hasOption(opt.getOpt())) {
			return commandLine.getOptionValue(opt.getOpt());
		}
		if (commandLine.hasOption(opt.getLongOpt())) {
			return commandLine.getOptionValue(opt.getLongOpt());
		}
		return null;
	}
	
}