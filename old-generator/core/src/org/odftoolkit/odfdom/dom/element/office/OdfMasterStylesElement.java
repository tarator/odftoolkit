/************************************************************************
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 *
 * Use is subject to license terms.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. You can also
 * obtain a copy of the License at http://odftoolkit.org/docs/license.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ************************************************************************/

/*
 * This file is automatically generated.
 * Don't edit manually.
 */    

package org.odftoolkit.odfdom.dom.element.office;

import org.odftoolkit.odfdom.dom.OdfName;
import org.odftoolkit.odfdom.dom.OdfNamespace;
import org.odftoolkit.odfdom.doc.OdfFileDom;
import org.odftoolkit.odfdom.dom.element.OdfContainerElementBase;

import org.odftoolkit.odfdom.dom.element.style.OdfMasterPageElement;
import org.odftoolkit.odfdom.dom.element.style.OdfHandoutMasterElement;
import org.odftoolkit.odfdom.dom.element.draw.OdfLayerSetElement;
import org.odftoolkit.odfdom.dom.element.table.OdfTableTemplateElement;
import org.odftoolkit.odfdom.dom.type.table.OdfRowOrColumnType;

/**
 * ODF DOM Element implementation for element "<office:master-styles>".
 */
public abstract class OdfMasterStylesElement extends OdfContainerElementBase
{        
    public static final OdfName ELEMENT_NAME = OdfName.get( OdfNamespace.OFFICE, "master-styles" );

    public OdfMasterStylesElement( OdfFileDom _aOwnerDoc )
    {
        super( _aOwnerDoc, ELEMENT_NAME );
    }

    public OdfName getOdfName()
    {
        return ELEMENT_NAME;
    }


    /**
    * Create child element "style:master-page".
    */
    public OdfMasterPageElement createMasterPageElement(String   _aName, String   _aPageLayoutName)
    {
        OdfMasterPageElement  _nMasterPage = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfMasterPageElement.class);
        _nMasterPage.setName( _aName);
        _nMasterPage.setPageLayoutName( _aPageLayoutName);
        this.appendChild( _nMasterPage);
        return  _nMasterPage;      
    }
    
    /**
    * Create child element "style:handout-master".
    */
    public OdfHandoutMasterElement createHandoutMasterElement(String   _aPageLayoutName)
    {
        OdfHandoutMasterElement  _nHandoutMaster = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfHandoutMasterElement.class);
        _nHandoutMaster.setPageLayoutName( _aPageLayoutName);
        this.appendChild( _nHandoutMaster);
        return  _nHandoutMaster;      
    }
    
    /**
    * Create child element "draw:layer-set".
    */
    public OdfLayerSetElement createLayerSetElement()
    {
        OdfLayerSetElement  _nLayerSet = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfLayerSetElement.class);
        this.appendChild( _nLayerSet);
        return  _nLayerSet;
    }                   
               
    /**
    * Create child element "table:table-template".
    */
    public OdfTableTemplateElement createTableTemplateElement(String   _aName, OdfRowOrColumnType   _aFirstRowStartColumn, OdfRowOrColumnType   _aFirstRowEndColumn, OdfRowOrColumnType   _aLastRowStartColumn, OdfRowOrColumnType   _aLastRowEndColumn)
    {
        OdfTableTemplateElement  _nTableTemplate = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfTableTemplateElement.class);
        _nTableTemplate.setName( _aName);
        _nTableTemplate.setFirstRowStartColumn( _aFirstRowStartColumn);
        _nTableTemplate.setFirstRowEndColumn( _aFirstRowEndColumn);
        _nTableTemplate.setLastRowStartColumn( _aLastRowStartColumn);
        _nTableTemplate.setLastRowEndColumn( _aLastRowEndColumn);
        this.appendChild( _nTableTemplate);
        return  _nTableTemplate;      
    }
    
}