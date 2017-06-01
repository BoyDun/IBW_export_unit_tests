package IBW_SBOL_update.make_doc;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import org.sbolstandard.core2.AccessType;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.OrientationType;
import org.sbolstandard.core2.SBOLConversionException;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.Sequence;
import org.sbolstandard.core2.SequenceAnnotation;
import org.sbolstandard.core2.SequenceOntology;


public class App 
{
	
	static BiocompilerModel biocompilerModel = new BiocompilerModel();
	
    public static void main( String[] args ) {

        ArrayList<Biopart> parts1 = new ArrayList<Biopart>();
        Biopart cell1part1 = new Biopart(2, "FirstCellFirstPart", "PROMOTER", "", 1, 1, "aa");
        Biopart cell1part2 = new Biopart(4, "FirstCellSecondPart", "GENE", "", 1, 2, "cccc");
        Biopart cell1part3 = new Biopart(10, "FirstCellFinalPart", "", "Random_url", 0, 3, "tttttttttt");
        parts1.add(cell1part1);
        parts1.add(cell1part2);
        parts1.add(cell1part3);
        Device d1 = new Device(parts1);
        ArrayList<Device> devices1 = new ArrayList<Device>();
        devices1.add(d1);
        biocompilerModel.addCell("FirstCell", devices1);
        
        ArrayList<Biopart> parts2 = new ArrayList<Biopart>();
        Biopart cell2part1 = new Biopart(2, "SecondCellFirstPart", "TERMINATOR", "", 0, 1, "at");
        Biopart cell2part2 = new Biopart(2, "SecondCellSecondPart", "random", "", 0, 2, "gc");
        parts2.add(cell2part1);
        parts2.add(cell2part2);
        Device d2 = new Device(parts2);
        ArrayList<Device> devices2 = new ArrayList<Device>();
        devices2.add(d2);
        biocompilerModel.addCell("SecondCell", devices2);
        
        try {
			SBOLDocument doc = makeSBOLDocument();
			doc.write("Test SBOL Document");
		} catch (SBOLValidationException e) {
			System.out.println("SBOLValidationException Thrown");
			e.printStackTrace();
		} catch (SBOLConversionException e) {
			System.out.println("SBOLConversionException Thrown");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("IOException Thrown");
			e.printStackTrace();
		}
    }
    
    public static String getSequence(Biopart bp) {
    	return bp.sequence;
    }
    
    public static SBOLDocument makeSBOLDocument() throws SBOLValidationException {
    	//PLACEHOLDER NAMESPACE. Implement user prompt later (as text field?)
    	String namespace = "file://dummy.org";
    	//SET NAMESPACE AS DEFAULT URI PREFIX
    	//PLACEHOLDER VERSION. Ask user to also specify version of document?
    	String version = "1";
    	//PULL PREFERENCE FROM IBW. Like HTTP vs FILE vs other scheme. assume it comes into ibw as a string as a parameter
    	//WRAPPER FUNCTION THAT CALLS ORIGINAL METHOD. USE WRAPPER FUNCTION TO FETCH FROM PREFERENCES.
    	SBOLDocument document = new SBOLDocument();
    	
    	document.setComplete(true); 			//Throw exceptions when URIs are incorrect
    	document.setCreateDefaults(true);	//Default components and/or functional component instances are created when not present
    	document.setTypesInURIs(false);		//Types aren't inserted into top-level identity URIs when they are created
    	document.setDefaultURIprefix(namespace);
    	
    	for (Cell c : biocompilerModel.cells) {
    		
    		ComponentDefinition dnaComponent = document.createComponentDefinition(c.name, version, ComponentDefinition.DNA);

    		//gather all parts in that cell
    		ArrayList<Biopart> allParts = new ArrayList<Biopart>();
    		for (Device d : c.devices){
    			allParts.addAll(d.parts);
    		}
    		
    		Collections.sort(allParts, (Biopart a, Biopart b) -> {
    			if(a.position.getValue() > b.position.getValue()) {
    				return 1;
    			}
    			return -1;
    		});
    		String wholeSequenceNucleotides = "";
			//Flip reverse orientation sequences?
    		Optional<String> reduced = allParts.stream().map((Biopart bp) -> {return bp.sequence;}).reduce((a,b)->a+b);
    		if(reduced.isPresent())	wholeSequenceNucleotides = reduced.get().toLowerCase();
    		Sequence wholeSequence = document.createSequence(c.name + "_sequence", version, wholeSequenceNucleotides, Sequence.IUPAC_DNA);

    		dnaComponent.addSequence(wholeSequence);

    		int sequenceStart = 1;
    		for (Biopart part : allParts) {
    			addSubcomponent(dnaComponent, document, part, sequenceStart, sequenceStart + part.sequenceLength - 1, version);
    			sequenceStart = sequenceStart + part.sequenceLength /*- 1 THIS CAUSES OFF BY ONE ERROR*/;
    		}

    	}

    	return document;
    }

    private static void addSubcomponent(ComponentDefinition compDef, SBOLDocument document, Biopart part,
    	int sequenceStart, int sequenceEnd, String version) throws SBOLValidationException {

    	OrientationType orientation = (part.direction == 1 ? OrientationType.INLINE : OrientationType.REVERSECOMPLEMENT);
    	//Ensure displayID is unique using counter
    	SequenceAnnotation curAnnotation = compDef.createSequenceAnnotation(part.name + "_annotation", "range", sequenceStart, sequenceEnd, orientation);
    	
    	URI partType;
    	// use the predefined SequenceOntology constant
    	switch (part.biologicalFunction) {
    		case "PROMOTER": { partType = SequenceOntology.PROMOTER; break; }
    		case "GENE": { partType = SequenceOntology.CDS; break; }
    		case "RBS": { partType = SequenceOntology.RIBOSOME_ENTRY_SITE; break; }
    		case "TERMINATOR": { partType = SequenceOntology.TERMINATOR; break; }
    		default: { partType = URI.create("http://identifiers.org/so/SO:0000110"); break; }
    	}

    	ComponentDefinition subCompDef = document.createComponentDefinition(part.name, version, ComponentDefinition.DNA);
    	subCompDef.addRole(partType);
    	Sequence partSequence = document.createSequence(part.name + "_sequence", version, part.sequence, Sequence.IUPAC_DNA);
    	subCompDef.addSequence(partSequence);
    	URI partURI;
    	if (part.accessionURL == null || part.accessionURL == "") {
    		//Remove this line and only include if there is an accession URL?
    		partURI = URI.create("http://sbols.org/" + part.name + "/dnaComponent");
    	}
    	else {
    		partURI = URI.create(part.accessionURL);
    	}
    	subCompDef.addWasDerivedFrom(partURI);
    	
    	compDef.createComponent(part.name, AccessType.PRIVATE, part.name, version);
    	curAnnotation.setComponent(part.name);

    }

//    TO UPDATE
//    def private static getSequenceFromNCL(String partName) {
//
//    	var String sequence
//    	try {
//    		var url = new URL("http://sbol.ncl.ac.uk:8081/part/" + partName + "/sbol").openStream
//    		//Is this URL readable
//    		var sbol = SBOLReader.read(url)
//    		//WHAT IS THIS DOING
//    		sequence = ((sbol?.contents?.get(0) as Collection)?.components?.get(0) as DnaComponent)?.dnaSequence?.
//    			nucleotides
//    	} catch (Exception e) {
//    		throw new UnknownPartInVirtualPartRepository(partName)
//    	}
//
//    	return sequence
//    }
    
}