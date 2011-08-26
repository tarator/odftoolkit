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

package org.odftoolkit.odfdom.dom.element.form;

import org.odftoolkit.odfdom.dom.OdfName;
import org.odftoolkit.odfdom.dom.OdfNamespace;
import org.odftoolkit.odfdom.doc.OdfFileDom;
import org.odftoolkit.odfdom.dom.element.OdfElement;
import org.odftoolkit.odfdom.dom.type.OdfStyleName;

import org.odftoolkit.odfdom.dom.type.form.OdfImagePositionType;

/**
 * ODF DOM Element implementation for element "<form:column>".
 */
public abstract class OdfColumnElement extends OdfElement
{        
    public static final OdfName ELEMENT_NAME = OdfName.get( OdfNamespace.FORM, "column" );

    public OdfColumnElement( OdfFileDom _aOwnerDoc )
    {
        super( _aOwnerDoc, ELEMENT_NAME );
    }

    public OdfName getOdfName()
    {
        return ELEMENT_NAME;
    }


    /**
     * Get value of attribute "form:name".
     */
    public String getName()
    {
        return getOdfAttribute( OdfName.get( OdfNamespace.FORM, "name" ) );
    }

    /**
     * Set value of attribute "form:name".
     */
    public void setName( String _aName )
    {
        setOdfAttribute( OdfName.get( OdfNamespace.FORM, "name" ), _aName );
    }

    /**
     * Get value of attribute "form:control-implementation".
     */
    public String getControlImplementation()
    {
        return getOdfAttribute( OdfName.get( OdfNamespace.FORM, "control-implementation" ) );
    }

    /**
     * Set value of attribute "form:control-implementation".
     */
    public void setControlImplementation( String _aControlImplementation )
    {
        setOdfAttribute( OdfName.get( OdfNamespace.FORM, "control-implementation" ), _aControlImplementation );
    }

    /**
     * Get value of attribute "form:label".
     */
    public String getLabel()
    {
        return getOdfAttribute( OdfName.get( OdfNamespace.FORM, "label" ) );
    }

    /**
     * Set value of attribute "form:label".
     */
    public void setLabel( String _aLabel )
    {
        setOdfAttribute( OdfName.get( OdfNamespace.FORM, "label" ), _aLabel );
    }

    /**
     * Get value of attribute "form:text-style-name".
     */
    public String getTextStyleName()
    {                    
        String aStringVal = getOdfAttribute( OdfName.get( OdfNamespace.FORM, "text-style-name" ) );
        return OdfStyleName.valueOf( aStringVal);
    }

    /**
     * Set value of attribute "form:text-style-name".
     */
    public void setTextStyleName( String _aTextStyleName )
    {                    
        String aStringVal = OdfStyleName.toString( _aTextStyleName );
        setOdfAttribute( OdfName.get( OdfNamespace.FORM, "text-style-name" ), aStringVal );
    }

    /**
    * Create child element "form:text".
    */
    public OdfTextElement createTextElement()
    {
        OdfTextElement  _nText = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfTextElement.class);
        this.appendChild( _nText);
        return  _nText;
    }                   
               
    /**
    * Create child element "form:textarea".
    */
    public OdfTextareaElement createTextareaElement()
    {
        OdfTextareaElement  _nTextarea = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfTextareaElement.class);
        this.appendChild( _nTextarea);
        return  _nTextarea;
    }                   
               
    /**
    * Create child element "form:formatted-text".
    */
    public OdfFormattedTextElement createFormattedTextElement()
    {
        OdfFormattedTextElement  _nFormattedText = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfFormattedTextElement.class);
        this.appendChild( _nFormattedText);
        return  _nFormattedText;
    }                   
               
    /**
    * Create child element "form:number".
    */
    public OdfNumberElement createNumberElement()
    {
        OdfNumberElement  _nNumber = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfNumberElement.class);
        this.appendChild( _nNumber);
        return  _nNumber;
    }                   
               
    /**
    * Create child element "form:date".
    */
    public OdfDateElement createDateElement()
    {
        OdfDateElement  _nDate = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfDateElement.class);
        this.appendChild( _nDate);
        return  _nDate;
    }                   
               
    /**
    * Create child element "form:time".
    */
    public OdfTimeElement createTimeElement()
    {
        OdfTimeElement  _nTime = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfTimeElement.class);
        this.appendChild( _nTime);
        return  _nTime;
    }                   
               
    /**
    * Create child element "form:combobox".
    */
    public OdfComboboxElement createComboboxElement()
    {
        OdfComboboxElement  _nCombobox = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfComboboxElement.class);
        this.appendChild( _nCombobox);
        return  _nCombobox;
    }                   
               
    /**
    * Create child element "form:listbox".
    */
    public OdfListboxElement createListboxElement()
    {
        OdfListboxElement  _nListbox = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfListboxElement.class);
        this.appendChild( _nListbox);
        return  _nListbox;
    }                   
               
    /**
    * Create child element "form:checkbox".
    */
    public OdfCheckboxElement createCheckboxElement()
    {
        OdfCheckboxElement  _nCheckbox = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfCheckboxElement.class);
        this.appendChild( _nCheckbox);
        return  _nCheckbox;      
    }
    
    /**
    * Create child element "form:checkbox".
    */
    public OdfCheckboxElement createCheckboxElement(OdfImagePositionType   _aImagePosition)
    {
        OdfCheckboxElement  _nCheckbox = ((OdfFileDom)this.ownerDocument).createOdfElement(OdfCheckboxElement.class);
        _nCheckbox.setImagePosition( _aImagePosition);
        this.appendChild( _nCheckbox);
        return  _nCheckbox;      
    }
    
}