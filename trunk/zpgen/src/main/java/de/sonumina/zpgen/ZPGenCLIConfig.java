package de.sonumina.zpgen;

import com.beust.jcommander.Parameter;

/**
 * The command line interface.
 *
 * @author Sebastian Bauer
 */
public class ZPGenCLIConfig
{
	@Parameter(names={"-z","--zfin-input-file"},required=true,description="The file containing the decomposed phenotype - gene associations (e.g., http://zfin.org/data_transfer/Downloads/phenotype.txt)")
	public String zfinFilePath;
	
	@Parameter(names={"-o","--ontology-output-file"},required=true,description="Where the ontology file (e.g. ZP.owl) is written to")
	public String ontoFilePath;
	
	@Parameter(names={"-a","--annotation-output-file"},required=true,description="Where the annotation file (e.g. ZP.annot) is written to")
	public String annotFilePath;
	
	@Parameter(names={"-k","--keep-ids"},required=false,description="If the output ontology file is already valid, keep the ids (ZP_nnnnnnn) stored in that file.")
	public boolean keepIds = false;
	
	@Parameter(names={"--add-source-information"},required=false,description="If set to true, add a tab delimited source information for the class expression to the ontology.")
	public boolean addSourceInformation = false;
	
	@Parameter(names={"-s","--source-information-output-file"},required=false,description="Add the source information to the ontology and save is also in a separate file. Implies --add-source-information set to true.")
	public String sourceInformationFile = null;
	
	@Parameter(names={"-h","--help"},help=true,description="Shows this help")
	public boolean help;
}
