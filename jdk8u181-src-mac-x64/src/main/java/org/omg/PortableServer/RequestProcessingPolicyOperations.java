package org.omg.PortableServer;


/**
* org/omg/PortableServer/RequestProcessingPolicyOperations.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /Users/java_re/workspace/8-2-build-macosx-x86_64/jdk8u181/11358/corba/src/share/classes/org/omg/PortableServer/poa.idl
* Saturday, July 7, 2018 1:04:13 AM PDT
*/


/**
	 * This policy specifies how requests are processed by 
	 * the created POA.  The default is 
	 * USE_ACTIVE_OBJECT_MAP_ONLY.
	 */
public interface RequestProcessingPolicyOperations  extends org.omg.CORBA.PolicyOperations
{

  /**
  	 * specifies the policy value
  	 */
  org.omg.PortableServer.RequestProcessingPolicyValue value ();
} // interface RequestProcessingPolicyOperations
