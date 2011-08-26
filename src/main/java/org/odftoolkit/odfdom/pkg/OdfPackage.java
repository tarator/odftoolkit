/************************************************************************
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * Copyright 2008, 2010 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 IBM. All rights reserved.
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
package org.odftoolkit.odfdom.pkg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import org.apache.xerces.dom.DOMXSImplementationSourceImpl;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.odftoolkit.odfdom.doc.OdfDocument.OdfMediaType;
import org.odftoolkit.odfdom.doc.OdfDocument;
import org.odftoolkit.odfdom.pkg.manifest.Algorithm;
import org.odftoolkit.odfdom.pkg.manifest.EncryptionData;
import org.odftoolkit.odfdom.pkg.manifest.KeyDerivation;
import org.odftoolkit.odfdom.pkg.manifest.OdfFileEntry;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * OdfPackage represents the package view to an OpenDocument document. The
 * OdfPackage will be created from an ODF document and represents a copy of the
 * loaded document, where files can be inserted and deleted. The changes take
 * effect, when the OdfPackage is being made persisted by save().
 */
public class OdfPackage implements Closeable {

	// Static parts of file references
	private static final String DOUBLE_DOT = "..";
	private static final String DOT = ".";
	private static final String SLASH = "/";
	private static final String COLON = ":";
	private static final String ENCODED_APOSTROPHE = "&apos;";
	private static final String ENCODED_QUOTATION = "&quot;";
	private static final String EMPTY_STRING = "";
	private static final String XML_MEDIA_TYPE = "text/xml";
	// Search patterns to be used in RegEx expressions
	private static final Pattern BACK_SLASH_PATTERN = Pattern.compile("\\\\");
	private static final Pattern DOUBLE_SLASH_PATTERN = Pattern.compile("//");
	private static final Pattern QUOTATION_PATTERN = Pattern.compile("\"");
	private static final Pattern APOSTROPHE_PATTERN = Pattern.compile("'");
	private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("\\p{Cntrl}");
	private static Set<String> mCompressedFileTypes;
	// some well known streams inside ODF packages
	private String mMediaType;
	private String mBaseURI;
	private ZipHelper mZipFile;
	private Resolver mResolver;
	private Map<String, ZipEntry> mZipEntries;
	private HashMap<String, ZipEntry> mOriginalZipEntries;
	private Map<String, OdfFileEntry> mFileEntries;
	// All opened documents from the same package are cached (including the root document)
	private Map<String, OdfPackageDocument> mPkgDocuments;
	// Three different incarnations of a package file/data
	// save() will check 1) mPkgDoms, 2) if not check mMemoryFileCache
	private HashMap<String, Document> mPkgDoms;
	private HashMap<String, byte[]> mMemoryFileCache;
	private ErrorHandler mErrorHandler;

	/* Commonly used files within the ODF Package */
	public enum OdfFile {

		/** The image directory is not defined by the OpenDocument standard, nevertheless the most spread ODF application OpenOffice.org is using the directory named "Pictures". */
		IMAGE_DIRECTORY("Pictures"),
		/** The "META-INF/manifest.xml" file is defined by the ODF 1.2 part 3 Package specification. This manifest is the 'content table' of the ODF package and describes the file entries of the ZIP including directories, but should not contain empty directories.*/
		MANIFEST("META-INF/manifest.xml"),
		/** The "mimetype" file is defined by the ODF 1.2 part 3 Package specification. It contains the mediatype string of the root document and must be the first file in the ZIP and must not be compressed. */
		MEDIA_TYPE("mimetype");
		private final String internalPath;

		OdfFile(String internalPath) {
			this.internalPath = internalPath;
		}

		public String getPath() {
			return internalPath;
		}
	}

	static {
		mCompressedFileTypes = new HashSet<String>();
		String[] typelist = new String[]{"jpg", "gif", "png", "zip", "rar",
			"jpeg", "mpe", "mpg", "mpeg", "mpeg4", "mp4", "7z", "ari",
			"arj", "jar", "gz", "tar", "war", "mov", "avi"};
		mCompressedFileTypes.addAll(Arrays.asList(typelist));
	}

	/**
	 * Creates the ODFPackage as an empty Package.
	 */
	private OdfPackage() {
		mMediaType = null;
		mResolver = null;
		mPkgDocuments = new HashMap<String, OdfPackageDocument>();
		mPkgDoms = new HashMap<String, Document>();
		mMemoryFileCache = new HashMap<String, byte[]>();
		mFileEntries = new HashMap<String, OdfFileEntry>();
		// specify whether validation should be enabled and what SAX ErrorHandler should be used.		
		if (mErrorHandler == null) {
			String errorHandlerProperty = System.getProperty("org.odftoolkit.odfdom.validation");
			if (errorHandlerProperty != null) {
				if (errorHandlerProperty.equalsIgnoreCase("true")) {
					mErrorHandler = new DefaultErrorHandler();
					Logger.getLogger(OdfPackage.class.getName()).info("Activated validation with default ErrorHandler!");
				} else {
					try {
						Class cl = Class.forName(errorHandlerProperty);
						Constructor ctor = cl.getDeclaredConstructor(new Class[]{});
						mErrorHandler = (ErrorHandler) ctor.newInstance();
						Logger.getLogger(OdfPackage.class.getName()).log(Level.INFO, "Activated validation with ErrorHandler:''{0}''!", errorHandlerProperty);
					} catch (Exception ex) {
						Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, "Could not initiate validation with the given ErrorHandler: '" + errorHandlerProperty + "'", ex);
					}
				}
			}
		}
	}

	/**
	 * Creates an OdfPackage from the OpenDocument provided by a File.
	 *
	 * <p>
	 * OdfPackage relies on the file being available for read access over the
	 * whole lifecycle of OdfPackage.
	 * </p>
	 *
	 * @param pkgFile
	 *            - a file representing the ODF document
	 * @throws java.lang.Exception
	 *             - if the package could not be created
	 */
	private OdfPackage(File pkgFile) throws Exception {
		this();
		mBaseURI = getBaseURLFromFile(pkgFile);
		initializeZip(new FileInputStream(pkgFile));
	}

	/**
	 * Creates an OdfPackage from the OpenDocument provided by a InputStream.
	 *
	 * <p>Since an InputStream does not provide the arbitrary (non sequentiell)
	 * read access needed by OdfPackage, the InputStream is cached. This usually
	 * takes more time compared to the other constructors. </p>
	 *
	 * @param packageStream - an inputStream representing the ODF package
	 * @param baseURI defining the base URI of ODF package.
	 * @param errorHandler - SAX ErrorHandler used for ODF validation
	 * @see #getErrorHandler
	 * @throws java.lang.Exception - if the package could not be created
	 * @see #getErrorHandler*
	 */
	private OdfPackage(InputStream packageStream, String baseURI, ErrorHandler errorHandler) throws Exception {
		this(); // calling private constructor
		mErrorHandler = errorHandler;
		mBaseURI = baseURI;
		initializeZip(packageStream);
	}

	/**
	 * Loads an OdfPackage from the given documentURL.
	 * 
	 * <p>
	 * OdfPackage relies on the file being available for read access over the
	 * whole lifecycle of OdfPackage.
	 * </p>
	 * 
	 * @param path
	 *            - the documentURL to the ODF package
	 * @return the OpenDocument document represented as an OdfPackage
	 * @throws java.lang.Exception
	 *             - if the package could not be loaded
	 */
	public static OdfPackage loadPackage(String path) throws Exception {
		File pkgFile = new File(path);
		return new OdfPackage(new FileInputStream(pkgFile), getBaseURLFromFile(pkgFile), null);
	}

	/**
	 * Loads an OdfPackage from the OpenDocument provided by a File.
	 * 
	 * <p>
	 * OdfPackage relies on the file being available for read access over the
	 * whole lifecycle of OdfPackage.
	 * </p>
	 * 
	 * @param pkgFile - the ODF Package
	 * @return the OpenDocument document represented as an OdfPackage
	 * @throws java.lang.Exception - if the package could not be loaded
	 */
	public static OdfPackage loadPackage(File pkgFile) throws Exception {
		return new OdfPackage(new FileInputStream(pkgFile), getBaseURLFromFile(pkgFile), null);
	}

	/**
	 * Creates an OdfPackage from the given InputStream.
	 * 
	 * <p>
	 * Since an InputStream does not provide the arbitrary (non sequentiell)
	 * read access needed by OdfPackage, the InputStream is cached. This usually
	 * takes more time compared to the other loadPackage methods.
	 * </p>
	 * 
	 * @param packageStream
	 *            - an inputStream representing the ODF package
	 * @return the OpenDocument document represented as an OdfPackage
	 * @throws java.lang.Exception
	 *             - if the package could not be loaded
	 */
	public static OdfPackage loadPackage(InputStream packageStream)
			throws Exception {
		return new OdfPackage(packageStream, null, null);
	}

	/**
	 * Creates an OdfPackage from the given InputStream.
	 *
	 * <p>OdfPackage relies on the file being available for read access over
	 * the whole lifecycle of OdfPackage.</p>
	 *
	 * @param packageStream - an inputStream representing the ODF package
	 * @param baseURI allows to explicitly set the base URI from the document, As the URL can not be derived from a stream.
	 * In addition it is possible to set the baseURI to any arbitrary URI, e.g. an URN.
	 * One usage of the baseURI to describe the source of validation exception thrown by the ErrorHandler.
	 * @param errorHandler - SAX ErrorHandler used for ODF validation
	 * @throws java.lang.Exception - if the package could not be created
	 * @see #getErrorHandler
	 */
	public static OdfPackage loadPackage(InputStream packageStream, String baseURI, ErrorHandler errorHandler) throws Exception {
		return new OdfPackage(packageStream, baseURI, errorHandler);
	}

	/**
	 * Loads an OdfPackage from the given File.
	 *
	 * <p>OdfPackage relies on the file being available for read access over
	 * the whole lifecycle of OdfPackage.</p>
	 * @param pkgFile - the ODF Package. A baseURL is being generated based on its location.
	 * @param errorHandler - SAX ErrorHandler used for ODF validation.
	 * @throws java.lang.Exception - if the package could not be created
	 * @see #getErrorHandler
	 */
	public static OdfPackage loadPackage(File pkgFile, ErrorHandler errorHandler) throws Exception {
		return new OdfPackage(new FileInputStream(pkgFile), getBaseURLFromFile(pkgFile), errorHandler);
	}

	// Initialize using memory
	private void initializeZip(InputStream odfStream) throws Exception {
		ByteArrayOutputStream tempBuf = new ByteArrayOutputStream();
		StreamHelper.transformStream(odfStream, tempBuf);
		byte[] mTempByteBuf = tempBuf.toByteArray();
		tempBuf.close();
		if (mTempByteBuf.length < 3) {
			OdfValidationException ve = new OdfValidationException(OdfPackageConstraint.PACKAGE_IS_NO_ZIP, getBaseURI());
			if (mErrorHandler != null) {
				mErrorHandler.fatalError(ve);
			}
			throw new IllegalArgumentException(ve);
		}
		mZipFile = new ZipHelper(this, mTempByteBuf);
		readZip();
	}

	private void readZip() throws SAXException, IOException {
		mZipEntries = new HashMap<String, ZipEntry>();
		String firstEntryName = mZipFile.entriesToMap(mZipEntries);
		if (mZipEntries.isEmpty()) {
			OdfValidationException ve = new OdfValidationException(OdfPackageConstraint.PACKAGE_IS_NO_ZIP, getBaseURI());
			if (mErrorHandler != null) {
				mErrorHandler.fatalError(ve);
			}
			throw new IllegalArgumentException(ve);
		} else {
			// initialize the files of the package (fileEnties of Manifest) 
			parseManifest();

			// initialize the package media type
			initializeMediaType(firstEntryName);

			// ToDo: Remove all META-INF/* files from the fileEntries of Manifest
			mOriginalZipEntries = new HashMap<String, ZipEntry>();
			mOriginalZipEntries.putAll(mZipEntries);
			mZipEntries.remove(OdfPackage.OdfFile.MEDIA_TYPE.getPath());
			mZipEntries.remove(OdfPackage.OdfFile.MANIFEST.getPath());
			mZipEntries.remove("META-INF/");
			if (mErrorHandler != null) {
				validateManifest();
			}
			Iterator<String> zipPaths = mZipEntries.keySet().iterator();
			while (zipPaths.hasNext()) {
				String internalPath = zipPaths.next();
				// every resource aside the /META-INF/manifest.xml (and META-INF/ directory)
				// and "mimetype" will be added as fileEntry
				if (!internalPath.equals(OdfPackage.OdfFile.MANIFEST.getPath())
						&& !internalPath.equals("META-INF/")
						&& !internalPath.equals(OdfPackage.OdfFile.MEDIA_TYPE.getPath())) {
					// aside "mediatype" and "META-INF/manifest"
					// add manifest entry as to be described by a <manifest:file-entry>
					ensureFileEntryExistence(internalPath);
				}
			}
		}
	}

	/** Validates if all file entries exist in the ZIP and vice versa */
	private void validateManifest() {
		Set zipPaths = mZipEntries.keySet();
		Set manifestPaths = mFileEntries.keySet();
		Set<String> sharedPaths = new HashSet<String>(zipPaths);
		sharedPaths.retainAll(manifestPaths);

		if (sharedPaths.size() < zipPaths.size()) {
			Set<String> zipPathSuperset = new HashSet<String>(mZipEntries.keySet());
			zipPathSuperset.removeAll(sharedPaths);
			Set sortedSet = new TreeSet<String>(zipPathSuperset);
			Iterator iter = sortedSet.iterator();
			String documentURL = getBaseURI();
			String internalPath;
			while (iter.hasNext()) {
				internalPath = (String) iter.next();
				if (!internalPath.endsWith(SLASH)) { // not for directories!
					logValidationError(OdfPackageConstraint.MANIFEST_DOES_NOT_LIST_FILE, documentURL, internalPath);
				}
			}
		}
		if (sharedPaths.size() < manifestPaths.size()) {
			Set<String> zipPathSubset = new HashSet<String>(mFileEntries.keySet());
			zipPathSubset.removeAll(sharedPaths);
			// removing root directory
			zipPathSubset.remove(SLASH);

			// No directory are listed in a ZIP removing all directory with content
			Iterator<String> manifestOnlyPaths = zipPathSubset.iterator();
			while (manifestOnlyPaths.hasNext()) {
				String manifestOnlyPath = manifestOnlyPaths.next();
				// assumption: all directories end with slash
				if (manifestOnlyPath.endsWith(SLASH)) {

					removeDirectory(manifestOnlyPath);
				} else {
					// if it is a nonexistent file
					logValidationError(OdfPackageConstraint.MANIFEST_LISTS_NONEXISTENT_FILE, getBaseURI(), manifestOnlyPath);
					mFileEntries.remove(manifestOnlyPath);
				}
			}
		}
		// remove none document directories
		Iterator<String> sharedPathsIter = sharedPaths.iterator();
		while (sharedPathsIter.hasNext()) {
			String sharedPath = sharedPathsIter.next();
			// assumption: all directories end with slash
			if (sharedPath.endsWith(SLASH)) {
				removeDirectory(sharedPath);
			}
		}
	}

	/** Removes directories without a mimetype (all none documents) */
	private void removeDirectory(String path) {
		if (path.endsWith(SLASH)) {
			// is it a sub-document?
			// assumption: if it has a mimetype...
			String dirMimeType = mFileEntries.get(path).getMediaTypeString();
			if (dirMimeType == null || EMPTY_STRING.equals(dirMimeType)) {
				logValidationWarning(OdfPackageConstraint.MANIFEST_LISTS_DIRECTORY, getBaseURI(), path);
				mFileEntries.remove(path);
			}
		}
	}

	/** Reads the uncompressed "mimetype" file, which contains the package media/mimte type*/
	private void initializeMediaType(String firstEntryName) {
		ZipEntry mimetypeEntry = mZipEntries.get(OdfPackage.OdfFile.MEDIA_TYPE.getPath());
		if (mimetypeEntry != null) {
			if (mErrorHandler != null) {
				validateMimeTypeEntry(mimetypeEntry, firstEntryName);
			}
			// get mediatype value of the root document/package from the mediatype file stream
			String entryMediaType = getMediaTypeFromEntry(mimetypeEntry);
			// get mediatype value of the root document/package from the manifest.xml
			String manifestMediaType = getMediaTypeFromManifest();
			// if a valid mediatype was set by the "mimetype" file
			if (entryMediaType != null && !entryMediaType.equals(EMPTY_STRING)) {
				// the root document's mediatype is taken from the "mimetype" file
				mMediaType = entryMediaType;
				if (mErrorHandler != null) {
					// if the "mediatype" does exist, the "/META-INF/manifest.xml" have to contain a MIMETYPE for the root document);
					if (manifestMediaType != null && !manifestMediaType.equals(EMPTY_STRING)) {
						// if the two media-types are inconsistent
						if (!entryMediaType.equals(manifestMediaType)) {
							logValidationError(OdfPackageConstraint.MIMETYPE_DIFFERS_FROM_PACKAGE, getBaseURI(), CONTROL_CHAR_PATTERN.matcher(mMediaType).replaceAll(EMPTY_STRING), manifestMediaType);
						}
					} else { // if "mimetype" file exists, there have to be a mimetype in the manifest.xml for the root document (see ODF 1.2 part 3)
						logValidationError(OdfPackageConstraint.MIMETYPE_WITHOUT_MANIFEST_MEDIATYPE, getBaseURI(), CONTROL_CHAR_PATTERN.matcher(mMediaType).replaceAll(EMPTY_STRING), manifestMediaType);
					}
				}
			} else { // if there is no media-type was set by the "mimetype" file
				// try as fall-back the mediatype of the root document from the manifest.xml
				if (manifestMediaType != null && !manifestMediaType.equals(EMPTY_STRING)) {
					// and used as fall-back for the mediatype of the package
					mMediaType = manifestMediaType;
				}
			}
		} else {
			String manifestMediaType = getMediaTypeFromManifest();
			if (manifestMediaType != null && !manifestMediaType.equals(EMPTY_STRING)) {
				// if not mimetype file exists, the root document mediaType from the manifest.xml is taken
				mMediaType = manifestMediaType;
			}
			if (mErrorHandler != null) {
				logValidationWarning(OdfPackageConstraint.MIMETYPE_NOT_IN_PACKAGE, getBaseURI());
			}
		}
	}

	private void validateMimeTypeEntry(ZipEntry mimetypeEntry, String firstEntryName) {

		if (mimetypeEntry.getMethod() != ZipEntry.STORED) {
			logValidationError(OdfPackageConstraint.MIMETYPE_IS_COMPRESSED, getBaseURI());
		}
		if (mimetypeEntry.getExtra() != null) {
			logValidationError(OdfPackageConstraint.MIMETYPE_HAS_EXTRA_FIELD, getBaseURI());
		}
		if (!OdfFile.MEDIA_TYPE.getPath().equals(firstEntryName)) {
			logValidationError(OdfPackageConstraint.MIMETYPE_NOT_FIRST_IN_PACKAGE, getBaseURI());
		}
	}

	/** @returns the media type of the root document from the manifest.xml */
	private String getMediaTypeFromManifest() {
		OdfFileEntry rootDocumentEntry = mFileEntries.get(SLASH);
		if (rootDocumentEntry != null) {
			return rootDocumentEntry.getMediaTypeString();
		} else {
			return null;
		}
	}

	/** @returns the media type of the root document from the manifest.xml */
	private String getMediaTypeFromEntry(ZipEntry mimetypeEntry) {
		String entryMediaType = null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			StreamHelper.transformStream(mZipFile.getInputStream(mimetypeEntry), out);
			entryMediaType = new String(out.toByteArray(), 0, out.size(), "UTF-8");
		} catch (Exception ex) {
			Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ex) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
				}
				out = null;
			}
		}
		return entryMediaType;
	}

	/**
	 * Insert an Odf document into the package at the given path.
	 * The path has to be a directory and will receive the MIME type of the OdfPackageDocument.
	 *
	 * @param doc the OdfPackageDocument to be inserted.
	 * @param internalPath
	 *		path relative to the package root, where the document should be inserted.
	 */
	void cacheDocument(OdfPackageDocument doc, String internalPath) {
		internalPath = normalizeDirectoryPath(internalPath);
		updateFileEntry(ensureFileEntryExistence(internalPath), doc.getMediaTypeString());
		mPkgDocuments.put(internalPath, doc);
	}

	/**
	 * Set the baseURI for this ODF package. NOTE: Should only be set during
	 * saving the package.
	 * @param baseURI defining the location of the package
	 */
	void setBaseURI(String baseURI) {
		mBaseURI = baseURI;
	}

	/**
	 * @return The URI to the ODF package, usually the URL, where this ODF package is located.
	 * If the package has not URI NULL is returned.
	 * This is the case if the package was new created without an URI and not saved before.
	 */
	public String getBaseURI() {
		return mBaseURI;
	}

	/**
	 * Returns on ODF documents based a given mediatype.
	 *
	 * @param internalPath path relative to the package root, where the document should be inserted.
	 * @return The ODF document, which mediatype dependends on the parameter or
	 *	NULL if media type were not supported.
	 */
	public OdfPackageDocument loadDocument(String internalPath) {
		OdfPackageDocument doc = getCachedDocument(internalPath);
		if (doc == null) {
			String mediaTypeString = getMediaTypeString();
			// ToDo: Remove dependency by factory - issue ??? (to be written)
			OdfMediaType odfMediaType = OdfMediaType.getOdfMediaType(mediaTypeString);
			if (odfMediaType == null) {
				doc = new OdfPackageDocument(this, internalPath, mediaTypeString);
			} else {
				try {
					String documentMediaType = getMediaTypeString(internalPath);
					odfMediaType = OdfMediaType.getOdfMediaType(documentMediaType);
					if (odfMediaType == null) {
						return null;
					}
					// ToDo: Remove dependency by facotory issue ??? (to be written)
					doc = OdfDocument.loadDocument(this, internalPath);
				} catch (Exception ex) {
					Logger.getLogger(OdfPackageDocument.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
		return doc;
	}

	/**
	 * @deprecated This method is only added temporary as workaround for the IBM fork using different DOC classes.
	 * Until the registering of DOC documents to the PKG layer has been finished.
	 * @param internalPath
	 *		path relative to the package root, where the document should be inserted.
	 * @return an already open OdfPackageDocument via its path, otherwise NULL.
	 */
	@Deprecated
	public OdfPackageDocument getCachedDocument(String internalPath) {
		internalPath = normalizeDirectoryPath(internalPath);
		return mPkgDocuments.get(internalPath);
	}

	/**
	 * @param dom
	 *		the DOM tree that has been parsed and should be added to the cache.
	 * @param internalPath
	 *		path relative to the package root, where the XML of the DOM is located.
	 * @return an already open OdfPackageDocument via its path, otherwise NULL.
	 */
	void cacheDom(Document dom, String internalPath) {
		internalPath = normalizeFilePath(internalPath);
		this.insert(dom, internalPath, null);
	}

	/**
	 * @param internalPath
	 *		path relative to the package root, where the document should be inserted.
	 * @return an already open W3C XML Documenet via its path, otherwise NULL.
	 */
	Document getCachedDom(String internalPath) {
		internalPath = normalizeFilePath(internalPath);
		return this.mPkgDoms.get(internalPath);
	}

	/**
	 * @return a map with all open W3C XML documents with their internal package path as key.
	 */
	Map<String, Document> getCachedDoms() {
		return this.mPkgDoms;
	}

	/**
	 * Removes a document from the package via its path. Independent if it was already opened or not.
	 * @param internalPath
	 *		path relative to the package root, where the document should be removed.
	 */
	public void removeDocument(String internalPath) {
		try {
			// get all files of the package
			Set<String> allPackageFileNames = getFileEntries();

			// If the document is the root document
			// the "/" representing the root document is outside the manifest.xml in the API an empty path
			if (internalPath.equals(OdfPackageDocument.ROOT_DOCUMENT_PATH)) {
				for (String entryName : allPackageFileNames) {
					remove(entryName);
				}
				remove(SLASH);
			} else {
				//remove all the stream of the directory, such as pictures
				List<String> directoryEntryNames = new ArrayList<String>();
				for (String entryName : allPackageFileNames) {
					if (entryName.startsWith(internalPath)) {
						directoryEntryNames.add(entryName);
					}
				}
				for (String entryName : directoryEntryNames) {
					remove(entryName);
				}
				remove(internalPath);
			}
		} catch (Exception ex) {
			Logger.getLogger(OdfPackageDocument.class.getName()).log(Level.SEVERE, null, ex);
		}

	}

	/** @return all currently opened OdfPackageDocument of this OdfPackage */
	Set<String> getCachedPackageDocuments() {
		return mPkgDocuments.keySet();
	}

	public OdfPackageDocument getRootDocument() {
		return mPkgDocuments.get(OdfPackageDocument.ROOT_DOCUMENT_PATH);
	}

	/**
	 * Get the media type of the ODF file or document (ie. a directory).
	 * A directory with a mediatype can be loaded as <code>OdfPackageDocument</code>.
	 *  Note: A directoy is represented by in the package as directory with media type
	 * @param internalPath within the package of the file or document.
	 * @return the mediaType for the resource of the given path	 
	 */
	public String getMediaTypeString(String internalPath) {
		String mediaType = null;
		if (internalPath != null) {
			if (internalPath.equals(EMPTY_STRING) || internalPath.equals(SLASH)) {
				return mMediaType;
			} else {
				mediaType = getMediaTypeFromEntry(normalizePath(internalPath));
				// if no file was found, look for a normalized directory name
				if (mediaType == null) {
					mediaType = getMediaTypeFromEntry(normalizeDirectoryPath(internalPath));
				}
			}
		}
		return mediaType;
	}

	private String getMediaTypeFromEntry(String internalPath) {
		OdfFileEntry entry = getFileEntry(internalPath);
		// if the document is not in the package, the return is NULL
		if (entry != null) {
			return entry.getMediaTypeString();
		} else {
			return null;
		}
	}

	/**
	 * Get the media type of the ODF package (equal to media type of ODF root
	 * document)
	 *
	 * @return the mediaType string of this ODF package
	 */
	public String getMediaTypeString() {
		return mMediaType;
	}

	/**
	 * Set the media type of the ODF package (equal to media type of ODF root
	 * document)
	 *
	 * @param mediaType
	 *            string of this ODF package
	 */
	void setMediaTypeString(String mediaType) {
		mMediaType = mediaType;
	}

	/**
	 * 
	 * Get an OdfFileEntry for the internalPath NOTE: This method should be
	 * better moved to a DOM inherited Manifest class
	 * 
	 * @param internalPath
	 *            The relative package path within the ODF package
	 * @return The manifest file entry will be returned.
	 */
	public OdfFileEntry getFileEntry(String internalPath) {
		internalPath = normalizeFilePath(internalPath);
		return getManifestEntries().get(internalPath);
	}

	/**
	 * Get a OdfFileEntries from the manifest file (i.e. /META/manifest.xml")
	 * 
	 * @return The manifest file entries will be returned.
	 */
	// 2DO: When moved to Manifest, after generation, the method might be renamed "Entries" to "Paths" as well
	public Set<String> getFileEntries() {
		return getManifestEntries().keySet();
	}

	/**
	 * 
	 * Check existence of a file in the package.
	 * 
	 * @param internalPath
	 *            The relative package documentURL within the ODF package
	 * @return True if there is an entry and a file for the given documentURL
	 */
	public boolean contains(String internalPath) {
		internalPath = normalizeFilePath(internalPath);
		return mFileEntries.containsKey(internalPath);
	}

	/**
	 * Save the package to given documentURL.
	 * 
	 * @param odfPath
	 *            - the path to the ODF package destination
	 * @throws java.lang.Exception
	 *             - if the package could not be saved
	 */
	public void save(String odfPath) throws Exception {
		File f = new File(odfPath);
		save(f);
	}

	/**
	 * Save package to a given File. After saving it is still necessary to close
	 * the package to have again full access about the file.
	 * 
	 * @param pkgFile
	 *            - the File to save the ODF package to
	 * @throws java.lang.Exception
	 *             - if the package could not be saved
	 */
	public void save(File pkgFile) throws Exception {
		String baseURL = getBaseURLFromFile(pkgFile);
//		if (baseURL.equals(mBaseURI)) {
//			// save to the same file: cache everything first
//			// ToDo: (Issue 219 - PackageRefactoring) --maybe it's better to write to a new file and copy that
//			// to the original one - would be less memory footprint
//			cacheContent();
//		}
		FileOutputStream fos = new FileOutputStream(pkgFile);
		save(fos, baseURL);
	}

	public void save(OutputStream odfStream) throws Exception {
		save(odfStream, null);
	}

	/**
	 * Save an ODF document to the OutputStream.
	 * 
	 * @param odfStream
	 *            - the OutputStream to insert content to
	 * @param baseURL defining the location of the package
	 * @throws java.lang.Exception
	 *             - if the package could not be saved
	 */
	private void save(OutputStream odfStream, String baseURL) {
		try {
			mBaseURI = baseURL;
			OdfFileEntry rootEntry = getManifestEntries().get(SLASH);
			if (rootEntry == null) {
				rootEntry = new OdfFileEntry(SLASH, mMediaType);
				getManifestEntries().put(SLASH, rootEntry);
			} else {
				rootEntry.setMediaTypeString(mMediaType);
			}
			ZipOutputStream zos = new ZipOutputStream(odfStream);

			// remove mediatype path and use it as first
			this.mFileEntries.remove(OdfFile.MEDIA_TYPE.getPath());
			Iterator<String> it = mFileEntries.keySet().iterator();
			String path = null;
			boolean isFirstFile = true;
			CRC32 crc = new CRC32();
			long modTime = (new java.util.Date()).getTime();
			while (it.hasNext() || isFirstFile) {
				try {
					byte[] data = null;
					// ODF requires the "mimetype" file to be at first in the package
					if (isFirstFile) {
						isFirstFile = false;
						// create "mimetype" from current attribute value
						data = mMediaType.getBytes("UTF-8");
						createZipEntry(OdfFile.MEDIA_TYPE.getPath(), data, zos, modTime, crc);
						// Create "META-INF/" directory
						createZipEntry("META-INF/", null, zos, modTime, crc);
						// Create "META-INF/manifest.xml" file
						data = getBytes(OdfFile.MANIFEST.getPath());
						createZipEntry(OdfFile.MANIFEST.getPath(), data, zos, modTime, crc);
					} else {
						path = it.next();
						// not interested to reuse previous mediaType nor manifest from ZIP
						if (!path.endsWith(SLASH) && !path.equals(OdfPackage.OdfFile.MANIFEST.getPath())
								&& !path.equals(OdfPackage.OdfFile.MEDIA_TYPE.getPath())) {
							data = getBytes(path);
							createZipEntry(path, data, zos, modTime, crc);
						}
					}
				} catch (IOException ex) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			zos.close();
			odfStream.flush();
		} catch (IOException ex) {
			Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void createZipEntry(String path, byte[] data, ZipOutputStream zos, long modTime, CRC32 crc) {
		ZipEntry ze = null;
		try {
			ze = mZipEntries.get(path);
			if (ze == null) {
				ze = new ZipEntry(path);
			}
			ze.setTime(modTime);
			if (fileNeedsCompression(path)) {
				ze.setMethod(ZipEntry.DEFLATED);
			} else {
				ze.setMethod(ZipEntry.STORED);
			}
			crc.reset();
			if (data != null) {
				ze.setSize(data.length);
				crc.update(data);
				ze.setCrc(crc.getValue());
			} else {
				ze.setSize(0);
				ze.setCrc(0);
			}
			ze.setCompressedSize(-1);
			zos.putNextEntry(ze);
			if (data != null) {
				zos.write(data, 0, data.length);
			}
			zos.closeEntry();
			mZipEntries.put(path, ze);
		} catch (IOException ex) {

			Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	/**
	 * Determines if a file have to be compressed.
	 * @param internalPath the file location
	 * @return true if the file needs compression, false, otherwise
	 */
	private boolean fileNeedsCompression(String internalPath) {
		boolean result = true;

		// ODF spec does not allow compression of "./mimetype" file
		if (internalPath.equals(OdfPackage.OdfFile.MEDIA_TYPE.getPath())) {
			return false;
		}
		// see if the file was already compressed
		if (internalPath.lastIndexOf(".") > 0) {
			String suffix = internalPath.substring(internalPath.lastIndexOf(".") + 1, internalPath.length());
			if (mCompressedFileTypes.contains(suffix.toLowerCase())) {
				result = false;
			}
		}
		return result;
	}


	/**
	 * Close the OdfPackage after it is no longer needed. Even after saving it
	 * is still necessary to close the package to have again full access about
	 * the file. Closing the OdfPackage will release all temporary created data.
	 * Do this as the last action to free resources. Closing an already closed
	 * document has no effect.
	 */
	public void close() {
		if (mZipFile != null) {
			try {
				mZipFile.close();
			} catch (IOException ex) {
				// log exception and continue
				Logger.getLogger(OdfPackage.class.getName()).log(Level.INFO,
						null, ex);
			}
		}
		// release all stuff - this class is impossible to use afterwards
		mZipFile = null;
		mMediaType = null;
		mZipEntries = null;
		mPkgDoms = null;
		mMemoryFileCache = null;
		mFileEntries = null;
		mBaseURI = null;
		mResolver = null;
	}

	/**
	 * Parse the Manifest file
	 */
	private void parseManifest() {
		InputStream is = null;
		try {
			ZipEntry entry = null;
			// loading the MANIFEST once from the ZIP, as it will never be cached, just once read
			// during load (now) and on save serialized from file status (ie. mFileEntries)
			if ((entry = mZipEntries.get(OdfPackage.OdfFile.MANIFEST.internalPath)) != null) {
				is = mZipFile.getInputStream(entry);
			}
			if (is == null) {
				logValidationError(OdfPackageConstraint.MANIFEST_NOT_IN_PACKAGE, getBaseURI());
				return;
			}
			XMLReader xmlReader = getXMLReader();
			xmlReader.setEntityResolver(getEntityResolver());
			xmlReader.setContentHandler(new OdfManifestSaxHandler(this));
			InputSource ins = new InputSource(is);
			String uri = mBaseURI + SLASH + OdfPackage.OdfFile.MANIFEST.internalPath;
			ins.setSystemId(uri);
			xmlReader.parse(ins);
			// ToDo: manifest.xml will be held in the future as DOM, now its being generated for each save()
			mMemoryFileCache.remove(OdfPackage.OdfFile.MANIFEST.internalPath);
		} catch (Exception ex) {
			Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			try {
				if (is != null) {
					is.close();
				}
			} catch (IOException ex) {
				Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}

	XMLReader getXMLReader() throws ParserConfigurationException,
			SAXException {
		// create sax parser
		SAXParserFactory saxFactory = new org.apache.xerces.jaxp.SAXParserFactoryImpl();
		saxFactory.setNamespaceAware(true);
		saxFactory.setValidating(false);
		try {
			saxFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		} catch (Exception ex) {
			Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
		}

		SAXParser parser = saxFactory.newSAXParser();
		XMLReader xmlReader = parser.getXMLReader();
		// More details at http://xerces.apache.org/xerces2-j/features.html#namespaces
		xmlReader.setFeature("http://xml.org/sax/features/namespaces", true);
		// More details at http://xerces.apache.org/xerces2-j/features.html#namespace-prefixes
		xmlReader.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
		// More details at http://xerces.apache.org/xerces2-j/features.html#xmlns-uris
		xmlReader.setFeature("http://xml.org/sax/features/xmlns-uris", true);
		return xmlReader;
	}

	// Add the given path and all its subdirectories to the internalPath list
	// to be written later to the manifest
	private void createSubEntries(String internalPath) {
		StringTokenizer tok = new StringTokenizer(internalPath, SLASH);
		if (tok.countTokens() > 1) {
			String path = EMPTY_STRING;
			while (tok.hasMoreTokens()) {
				String directory = tok.nextToken();
				// it is a directory, if there are more token
				if (tok.hasMoreTokens()) {
					path = path + directory + SLASH;
					OdfFileEntry fileEntry = mFileEntries.get(path);
					if (fileEntry == null) {
						mFileEntries.put(path, new OdfFileEntry(path, null));
					}
				}
			}
		}
	}

	/**
	 * Insert DOM tree into OdfPackage. An existing file will be replaced.
	 *
	 * @param fileDOM
	 *            - XML DOM tree to be inserted as file. 
	 * @param internalPath
	 *            - relative documentURL where the DOM tree should be inserted as XML file
	 * @param mediaType
	 *            - media type of stream. Set to null if unknown
	 * @throws java.lang.Exception
	 *             when the DOM tree could not be inserted
	 */
	public void insert(Document fileDOM, String internalPath, String mediaType) {
		internalPath = normalizeFilePath(internalPath);
		if (mediaType == null) {
			mediaType = XML_MEDIA_TYPE;
		}
		if (fileDOM == null) {
			mPkgDoms.remove(internalPath);
		} else {
			mPkgDoms.put(internalPath, fileDOM);
		}
		updateFileEntry(ensureFileEntryExistence(internalPath), mediaType);
		// remove byte array version of new DOM
		mMemoryFileCache.remove(internalPath);
	}

	/**
	 * Embed an OdfPackageDocument to the current OdfPackage.
	 * All the file entries of child document will be inserted.
	 * @param sourceDocument the OdfPackageDocument to be embedded.
	 * @param internalPath path to the directory the ODF document should be inserted (relative to ODF package root).
	 */
	public void insertDocument(OdfPackageDocument sourceDocument, String internalPath) {
		// opened DOM of descendant Documents will be flashed to the their pkg
		flushDoms(sourceDocument);

		// Gets the OdfDocument's manifest entry info, no matter it is a independent document or an embeddedDocument.
		Map<String, OdfFileEntry> entryMapToCopy;
		if (sourceDocument.isRootDocument()) {
			entryMapToCopy = sourceDocument.getPackage().getManifestEntries();
		} else {
			entryMapToCopy = sourceDocument.getPackage().getSubDirectoryEntries(sourceDocument.getDocumentPath());
		}
		//insert to package and add it to the Manifest
		internalPath = sourceDocument.setDocumentPath(internalPath);
		Set<String> entryNameList = entryMapToCopy.keySet();
		for (String entryName : entryNameList) {
			OdfFileEntry entry = entryMapToCopy.get(entryName);
			if (entry != null) {
				try {
					// if entry is a directory (e.g. an ODF document root)
					if (entryName.endsWith(SLASH)) {
						// insert directory
						if (entryName.equals(SLASH)) {
							insert((byte[]) null, internalPath, sourceDocument.getMediaTypeString());
						} else {
							insert((byte[]) null, internalPath + entry.getPath(), entry.getMediaTypeString());
						}
					} else {
						String packagePath = internalPath + entry.getPath();
						insert(sourceDocument.getPackage().getInputStream(entryName), packagePath, entry.getMediaTypeString());
					}
				} catch (Exception ex) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
		//make sure the media type of embedded Document is right set.
		OdfFileEntry embedDocumentRootEntry = new OdfFileEntry(internalPath, sourceDocument.getMediaTypeString());
		getManifestEntries().put(internalPath, embedDocumentRootEntry);
		// the new document will be attached to its new package (it has been inserted to)
		sourceDocument.setPackage(this);
		cacheDocument(sourceDocument, internalPath);
	}

	/** 
	 * Insert all open DOMs of XML files beyond parent document to the package.
	 * The XML files will be updated in the package after calling save.
	 *
	 * @param parentDocument the document, which XML files shall be serialized
	 */
	void flushDoms(OdfPackageDocument parentDocument) {
		OdfPackage pkg = parentDocument.getPackage();
		if (parentDocument.isRootDocument()) {
			// for every parsed XML file (DOM)
			for (String xmlFilePath : pkg.getCachedDoms().keySet()) {
				// insert it to the package (serializing and caching it till final save)
				pkg.insert(pkg.getCachedDom(xmlFilePath), xmlFilePath, "text/xml");
			}
		} else {
			// if not root document, check ..
			String parentDocumentPath = parentDocument.getDocumentPath();
			// for every parsed XML file (DOM)
			for (String xmlFilePath : pkg.getCachedDoms().keySet()) {
				// if the file is within the given document
				if (xmlFilePath.startsWith(parentDocumentPath)) {
					// insert it to the package (serializing and caching it till final save)
					pkg.insert(pkg.getCachedDom(xmlFilePath), xmlFilePath, "text/xml");
				}
			}
		}
	}

	/** Get all the file entries from a sub directory */
	private Map<String, OdfFileEntry> getSubDirectoryEntries(String directory) {
		directory = normalizeDirectoryPath(directory);
		Map<String, OdfFileEntry> subEntries = new HashMap<String, OdfFileEntry>();
		Map<String, OdfFileEntry> allEntries = getManifestEntries();
		Set<String> rootEntryNameSet = getFileEntries();
		for (String entryName : rootEntryNameSet) {
			if (entryName.startsWith(directory)) {
				String newEntryName = entryName.substring(directory.length());
				if (newEntryName.length() == 0) {
					continue;
				}
				OdfFileEntry srcFileEntry = allEntries.get(entryName);
				OdfFileEntry newFileEntry = new OdfFileEntry();
				newFileEntry.setEncryptionData(srcFileEntry.getEncryptionData());
				newFileEntry.setMediaTypeString(srcFileEntry.getMediaTypeString());
				newFileEntry.setPath(newEntryName);
				newFileEntry.setSize(srcFileEntry.getSize());
				subEntries.put(entryName, newFileEntry);
			}
		}
		return subEntries;
	}

	/**
	 * Method returns the paths of all document within the package.
	 *
	 * @return A set of paths of all documents of the package, including the root document.
	 */
	public Set<String> getDocumentPaths() {
		return getDocumentPaths(null, null);
	}

	/**
	 * Method returns the paths of all document within the package matching the given criteria.
	 *
	 * @param mediaTypeString limits the desired set of document paths to documents of the given mediaType
	 * @return A set of paths of all documents of the package, including the root document, that match the given parameter.
	 */
	public Set<String> getDocumentPaths(String mediaTypeString) {
		return getDocumentPaths(mediaTypeString, null);
	}

	/**
	 * Method returns the paths of all document within the package matching the given criteria.
	 *
	 * @param mediaTypeString limits the desired set of document paths to documents of the given mediaType
	 * @param subDirectory limits the desired set document paths to those documents below of this subdirectory
	 * @return A set of paths of all documents of the package, including the root document, that match the given parameter.
	 */
	Set<String> getDocumentPaths(String mediaTypeString, String subDirectory) {
		Set<String> innerDocuments = new HashSet<String>();
		Set<String> packageFilePaths = getFileEntries();
		// check manifest for current embedded OdfPackageDocuments
		for (String filePath : packageFilePaths) {
			// check if a subdirectory was the criteria and if the files are beyond the given subdirectory
			if (subDirectory == null || filePath.startsWith(subDirectory) && !filePath.equals(subDirectory)) {
				// with documentURL is not empty and is a directory (ie. a potential document)
				if (filePath.length() > 1 && filePath.endsWith(SLASH)) {
					String fileMediaType = getFileEntry(filePath).getMediaTypeString();
					if (fileMediaType != null && !fileMediaType.equals(EMPTY_STRING)) {
						// check if a certain mediaType was the critera and was matched
						if (mediaTypeString == null || mediaTypeString.equals(fileMediaType)) {
							// only relative path is allowed as path
							innerDocuments.add(filePath);
						}
					}
				}
			}
		}
		return innerDocuments;
	}

	/**
	 * Adding a manifest:file-entry to be saved in manifest.xml. 
	 * In addition, sub directories will be added as well to the manifest.
	 */
	private OdfFileEntry ensureFileEntryExistence(String internalPath) {
		// if it is NOT the resource "/META-INF/manifest.xml"
		OdfFileEntry fileEntry = null;
		if (!OdfPackage.OdfFile.MANIFEST.internalPath.equals(internalPath)) {
			if (mFileEntries == null) {
				mFileEntries = new HashMap<String, OdfFileEntry>();
			}
			fileEntry = mFileEntries.get(internalPath);
			// for every new file entry
			if (fileEntry == null) {
				fileEntry = new OdfFileEntry(internalPath);
				mFileEntries.put(internalPath, fileEntry);
				// creates recursive file entries for all sub directories
				createSubEntries(internalPath);
			}
		}
		return fileEntry;
	}

	/**
	 * update file entry setting.
	 */
	private void updateFileEntry(OdfFileEntry fileEntry, String mediaType) {
		// overwrite previous settings
		fileEntry.setMediaTypeString(mediaType);
		// reset encryption data (ODFDOM does not support encryption yet)
		fileEntry.setEncryptionData(null);
		// reset size to be unset
		fileEntry.setSize(-1);
	}

	/**
	 * Gets org.w3c.dom.Document for XML file contained in package.
	 * 
	 * @param internalPath to a file within the Odf Package (eg. content.xml)
	 * @return an org.w3c.dom.Document
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws TransformerConfigurationException
	 * @throws TransformerException
	 */
	public Document getDom(String internalPath) throws SAXException,
			ParserConfigurationException, IllegalArgumentException,
			TransformerConfigurationException, TransformerException, IOException {

		Document doc = mPkgDoms.get(internalPath);
		if (doc != null) {
			return doc;
		}

		InputStream is = getInputStream(internalPath);

		// We depend on Xerces. So we just go ahead and create a Xerces DBF,
		// without
		// forcing everything else to do so.
		DocumentBuilderFactory factory = new org.apache.xerces.jaxp.DocumentBuilderFactoryImpl();
		factory.setNamespaceAware(true);
		factory.setValidating(false);

		DocumentBuilder builder = factory.newDocumentBuilder();
		builder.setEntityResolver(getEntityResolver());

		String uri = getBaseURI() + internalPath;

		if (mErrorHandler != null) {
			builder.setErrorHandler(mErrorHandler);
		}

		InputSource ins = new InputSource(is);
		ins.setSystemId(uri);

		doc = builder.parse(ins);

		if (doc != null) {
			mPkgDoms.put(internalPath, doc);
			mMemoryFileCache.remove(internalPath);
		}
		return doc;
	}

	/**
	 * Inserts an external file into an OdfPackage. An existing file will be
	 * replaced.
	 * 
	 * @param sourceURI
	 *            - the source URI to the file to be inserted into the package.
	 * @param internalPath
	 *            - relative documentURL where the tree should be inserted as XML
	 *            file
	 * @param mediaType
	 *            - media type of stream. Set to null if unknown
	 * @throws java.lang.Exception
	 *             In case the file could not be saved
	 */
	public void insert(URI sourceURI, String internalPath, String mediaType)
			throws Exception {
		InputStream is = null;
		if (sourceURI.isAbsolute()) {
			// if the URI is absolute it can be converted to URL
			is = sourceURI.toURL().openStream();
		} else {
			// otherwise create a file class to open the stream
			is = new FileInputStream(sourceURI.toString());
			// ToDo: (Issue 219 - PackageRefactoring) --error handling in this case! -> allow method insert(URI,
			// ppath, mtype)?
		}
		insert(is, internalPath, mediaType);
	}

	/**
	 * Inserts InputStream into an OdfPackage. An existing file will be
	 * replaced.
	 * 
	 * @param fileStream
	 *            - the stream of the file to be inserted into the ODF package.
	 * @param internalPath
	 *            - relative documentURL where the tree should be inserted as XML
	 *            file
	 * @param mediaType
	 *            - media type of stream. Set to null if unknown
	 */
	public void insert(InputStream fileStream, String internalPath, String mediaType) throws Exception {
		internalPath = normalizeFilePath(internalPath);
		if (fileStream == null) {
			//adding a simple directory without MIMETYPE
			insert((byte[]) null, internalPath, mediaType);
		} else {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			BufferedInputStream bis = null;
			if (fileStream instanceof BufferedInputStream) {
				bis = (BufferedInputStream) fileStream;
			} else {
				bis = new BufferedInputStream(fileStream);
			}
			StreamHelper.transformStream(bis, baos);
			byte[] data = baos.toByteArray();
			insert(data, internalPath, mediaType);
		}
	}

	/**
	 * Inserts a byte array into OdfPackage. An existing file will be replaced.
	 * If the byte array is NULL a directory with the given mimetype will be created.
	 *
	 * @param fileBytes
	 *      - data of the file stream to be stored in package.
	 *		If NULL a directory with the given mimetype will be created.
	 * @param internalPath
	 *      - path of the file or directory relative to the package root.
	 * @param mediaTypeString
	 *      - media type of stream. If unknown null can be used.
	 */
	public void insert(byte[] fileBytes, String internalPath, String mediaTypeString) {
		internalPath = normalizeFilePath(internalPath);
		if (OdfPackage.OdfFile.MEDIA_TYPE.getPath().equals(internalPath)) {
			try {
				setMediaTypeString(new String(fileBytes, "UTF-8"));
			} catch (UnsupportedEncodingException useEx) {
				Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE,
						"ODF file could not be created as string!", useEx);
			}
			return;
		}
		if (fileBytes != null) {
			mMemoryFileCache.put(internalPath, fileBytes);
			// as DOM would overwrite data cache, any existing DOM cache will be deleted
			if (mPkgDoms.containsKey(internalPath)) {
				mPkgDoms.remove(internalPath);
			}
		}
		updateFileEntry(ensureFileEntryExistence(internalPath), mediaTypeString);
	}

	// changed to package access as the manifest interiors are an implementation detail
	Map<String, OdfFileEntry> getManifestEntries() {
		return mFileEntries;
	}

	/**
	 * Get Manifest as String NOTE: This functionality should better be moved to
	 * a DOM based Manifest class
	 *
	 * @return the /META-INF/manifest.xml as a String
	 */
	public String getManifestAsString() {
		if (mFileEntries == null) {
			return null;
		} else {
			StringBuilder buf = new StringBuilder();
			buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			buf.append("<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\">\n");
			Iterator<String> it = new TreeSet<String>(mFileEntries.keySet()).iterator();
			while (it.hasNext()) {
				String key = it.next();
				String s = null;
				OdfFileEntry fileEntry = mFileEntries.get(key);
				if (fileEntry != null) {
					s = fileEntry.getPath();
					// only directories with a mimetype (documents) will be written into the manifest.xml
					if (s != null && !s.endsWith(SLASH) || !fileEntry.getMediaTypeString().equals(EMPTY_STRING)) {
						buf.append(" <manifest:file-entry");
						if (s != null) {
							buf.append(" manifest:full-path=\"");
							buf.append(encodeXMLAttributes(s));
							buf.append("\"");

						}
						s = fileEntry.getMediaTypeString();
						buf.append(" manifest:media-type=\"");
						buf.append(encodeXMLAttributes(s));
						buf.append("\"");

						int i = fileEntry.getSize();
						if (i > 0) {
							buf.append(" manifest:size=\"");
							buf.append(i);
							buf.append("\"");
						}
						EncryptionData enc = fileEntry.getEncryptionData();
						if (enc != null) {
							buf.append(">\n");
							buf.append("  <manifest:encryption-data>\n");
							Algorithm alg = enc.getAlgorithm();
							if (alg != null) {
								buf.append("   <manifest:algorithm");
								s = alg.getName();
								if (s != null) {
									buf.append(" manifest:algorithm-name=\"");
									buf.append(encodeXMLAttributes(s));
									buf.append("\"");
								}
								s = alg.getInitializationVector();
								if (s != null) {
									buf.append(" manifest:initialization-vector=\"");
									buf.append(encodeXMLAttributes(s));
									buf.append("\"");
								}
								buf.append("/>\n");
							}
							KeyDerivation keyDerivation = enc.getKeyDerivation();
							if (keyDerivation != null) {
								buf.append("   <manifest:key-derivation");
								s = keyDerivation.getName();
								if (s != null) {
									buf.append(" manifest:key-derivation-name=\"");
									buf.append(encodeXMLAttributes(s));
									buf.append("\"");
								}
								s = keyDerivation.getSalt();
								if (s != null) {
									buf.append(" manifest:salt=\"");
									buf.append(encodeXMLAttributes(s));
									buf.append("\"");
								}
								buf.append(" manifest:iteration-count=\"");
								buf.append(keyDerivation.getIterationCount());
								buf.append("\"/>\n");
							}
							buf.append("  </manifest:encryption-data>\n");
							buf.append(" </<manifest:file-entry>\n");
						} else {
							buf.append("/>\n");
						}
					}
				}
			}
			buf.append("</manifest:manifest>");
			return buf.toString();
		}
	}

	/**
	 * Get package (sub-) content as byte array
	 * 
	 * @param internalPath relative documentURL to the package content
	 * @return the unzipped package content as byte array
	 * @throws java.lang.Exception
	 */
	public byte[] getBytes(String internalPath) {
		// if path is null or empty return null
		if (internalPath == null || internalPath.equals(EMPTY_STRING)) {
			return null;
		}
		internalPath = normalizeFilePath(internalPath);
		byte[] data = null;
		// if the file is "mimetype"
		if (internalPath.equals(OdfPackage.OdfFile.MEDIA_TYPE.getPath())) {
			if (mMediaType == null) {
				return null;
			} else {
				try {
					data = mMediaType.getBytes("UTF-8");
				} catch (UnsupportedEncodingException use) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, use);
					return null;
				}
			}
			// if the file is "/META-INF/manifest.xml"
		} else if (internalPath.equals(OdfPackage.OdfFile.MANIFEST.internalPath)) {
			if (mFileEntries == null) {
				// manifest was not present
				return null;
			}
			String s = getManifestAsString();
			if (s == null) {
				return null;
			} else {
				try {
					data = s.getBytes("UTF-8");
				} catch (UnsupportedEncodingException ex) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			// if the path is already loaded as DOM (highest priority)
		} else if (mPkgDoms.get(internalPath) != null) {
			data = flushDom(mPkgDoms.get(internalPath));
			mMemoryFileCache.put(internalPath, data);
			// if the path's file was cached to memory (second high priority)
		} else if (mFileEntries.containsKey(internalPath)
				&& mMemoryFileCache.get(internalPath) != null) {
			data = mMemoryFileCache.get(internalPath);

			// if the path's file was cached to disc (lowest priority)
		}
		// if not available, check if file exists in ZIP
		if (data == null) {
			ZipEntry entry = null;
			if ((entry = mZipEntries.get(internalPath)) != null) {
				InputStream inputStream = null;
				try {
					inputStream = mZipFile.getInputStream(entry);
					if (inputStream != null) {
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						StreamHelper.transformStream(inputStream, out);
						data = out.toByteArray();
						// store for further usage; do not care about manifest: that
						// is handled exclusively
						mMemoryFileCache.put(internalPath, data);
					}
				} catch (IOException ex) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
				} finally {
					try {
						if (inputStream != null) {
							inputStream.close();
						}
					} catch (IOException ex) {
						Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			}
		}
		return data;
	}

	// Serializes a DOM tree into a byte array.
	// Providing the counterpart of the generic Namespace handling of OdfFileDom.
	private byte[] flushDom(Document dom) {
		// if it is one of our DOM files we may flush all collected namespaces to the root element
		if (dom instanceof OdfFileDom) {
			OdfFileDom odfDom = (OdfFileDom) dom;
			Map<String, String> nsByUri = odfDom.getMapNamespacePrefixByUri();
			OdfElement root = odfDom.getRootElement();
			if (root != null) {
				for (Entry<String, String> entry : nsByUri.entrySet()) {
					root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + entry.getValue(), entry.getKey());
				}
			}
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DOMXSImplementationSourceImpl dis = new org.apache.xerces.dom.DOMXSImplementationSourceImpl();
		DOMImplementationLS impl = (DOMImplementationLS) dis.getDOMImplementation("LS");
		LSSerializer writer = impl.createLSSerializer();
		LSOutput output = impl.createLSOutput();
		output.setByteStream(baos);
		writer.write(dom, output);
		return baos.toByteArray();
	}

	/**
	 * Get the latest version of package content as InputStream, as it would be saved.
	 * This might not be the original version once loaded from the package.
	 * 
	 * @param internalPath
	 *            of the desired stream.
	 * @return Inputstream of the ODF file within the package for the given
	 *         path.
	 */
	public InputStream getInputStream(String internalPath) {
		internalPath = normalizeFilePath(internalPath);
		// else we always cache here and return a ByteArrayInputStream because
		// if
		// we would return ZipFile getInputStream(entry) we would not be
		// able to read 2 Entries at the same time. This is a limitation of the
		// ZipFile class.
		// As it would be quite a common thing to read the content.xml and the
		// styles.xml
		// simultanously when using XSLT on OdfPackages we want to circumvent
		// this limitation
		byte[] data = getBytes(internalPath);
		if (data != null && data.length != 0) {
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			return bais;
		}
		return null;
	}

	/**
	 * Get the latest version of package content as InputStream, as it would be saved.
	 * This might not be the original version once loaded from the package.
	 *
	 * @param internalPath
	 *            of the desired stream.
	 * @param useOriginal true uses the stream as loaded from the ZIP.
	 *				False will return even modified file content as a stream.
	 * @return Inputstream of the ODF file within the package for the given
	 *         path.
	 */
	public InputStream getInputStream(String internalPath, boolean useOriginal) {
		InputStream stream = null;
		if (useOriginal) {
			ZipEntry entry = mOriginalZipEntries.get(internalPath);
			if (entry != null) {
				try {
					stream = mZipFile.getInputStream(entry);
				} catch (IOException ex) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		} else {
			stream = getInputStream(internalPath);
		}
		return stream;
	}

	/**
	 * Gets the InputStream containing whole OdfPackage.
	 * 
	 * @return the ODF package as input stream
	 * @throws java.lang.Exception
	 *             - if the package could not be read
	 */
	public InputStream getInputStream() throws Exception {
		final PipedOutputStream os = new PipedOutputStream();
		final PipedInputStream is = new PipedInputStream();

		is.connect(os);

		Thread thread1 = new Thread() {

			@Override
			public void run() {
				try {
					save(os, mBaseURI);
				} catch (Exception e) {
				}
			}
		};

		Thread thread2 = new Thread() {

			@Override
			public void run() {
				try {
					BufferedInputStream bis = new BufferedInputStream(is, StreamHelper.PAGE_SIZE);
					BufferedOutputStream bos = new BufferedOutputStream(os, StreamHelper.PAGE_SIZE);
					StreamHelper.transformStream(bis, bos);
					is.close();
					os.close();
				} catch (Exception ie) {
				}
			}
		};

		thread1.start();
		thread2.start();

		return is;
	}

	/**
	 * Insert the OutputStream for into OdfPackage. An existing file will be
	 * replaced.
	 * 
	 * @param internalPath
	 *            - relative documentURL where the DOM tree should be inserted as
	 *            XML file
	 * @return outputstream for the data of the file to be stored in package
	 * @throws java.lang.Exception
	 *             when the DOM tree could not be inserted
	 */
	public OutputStream insertOutputStream(String internalPath) throws Exception {
		return insertOutputStream(internalPath, null);
	}

	/**
	 * Insert the OutputStream - to be filled after method - when stream is
	 * closed into OdfPackage. An existing file will be replaced.
	 * 
	 * @param internalPath
	 *            - relative documentURL where the DOM tree should be inserted as
	 *            XML file
	 * @param mediaType
	 *            - media type of stream
	 * @return outputstream for the data of the file to be stored in package
	 * @throws java.lang.Exception
	 *             when the DOM tree could not be inserted
	 */
	public OutputStream insertOutputStream(String internalPath, String mediaType)
			throws Exception {
		internalPath = normalizeFilePath(internalPath);
		final String fPath = internalPath;
		final OdfFileEntry fFileEntry = getFileEntry(internalPath);
		final String fMediaType = mediaType;

		ByteArrayOutputStream baos = new ByteArrayOutputStream() {

			@Override
			public void close() {
				try {
					byte[] data = this.toByteArray();
					if (fMediaType == null || fMediaType.length() == 0) {
						insert(data, fPath, fFileEntry == null ? null
								: fFileEntry.getMediaTypeString());
					} else {
						insert(data, fPath, fMediaType);
					}
					super.close();
				} catch (Exception ex) {
					Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		};
		return baos;
	}

	/** Removes a singel file from the package.
	 * @param internalPath of the file relative to the package root
	 */
	public void remove(String internalPath) {
		internalPath = normalizePath(internalPath);
		if (mZipEntries != null && mZipEntries.containsKey(internalPath)) {
			mZipEntries.remove(internalPath);
		}
		if (mFileEntries != null && mFileEntries.containsKey(internalPath)) {
			mFileEntries.remove(internalPath);
		}
	}

	/**
	 * Encoded XML Attributes
	 */
	private String encodeXMLAttributes(String attributeValue) {
		String encodedValue = QUOTATION_PATTERN.matcher(attributeValue).replaceAll(ENCODED_QUOTATION);
		encodedValue = APOSTROPHE_PATTERN.matcher(encodedValue).replaceAll(ENCODED_APOSTROPHE);
		return encodedValue;
	}

	/**
	 * Get EntityResolver to be used in XML Parsers which can resolve content
	 * inside the OdfPackage
	 * 
	 * @return a SAX EntityResolver
	 */
	public EntityResolver getEntityResolver() {
		if (mResolver == null) {
			mResolver = new Resolver(this);
		}
		return mResolver;
	}

	/**
	 * Get URIResolver to be used in XSL Transformations which can resolve
	 * content inside the OdfPackage
	 * 
	 * @return a TraX Resolver
	 */
	public URIResolver getURIResolver() {
		if (mResolver == null) {
			mResolver = new Resolver(this);
		}
		return mResolver;
	}

	private static String getBaseURLFromFile(File file) throws Exception {
		String baseURL = file.getCanonicalFile().toURI().toString();
		baseURL = BACK_SLASH_PATTERN.matcher(baseURL).replaceAll(SLASH);
		return baseURL;
	}

	/**
	 * Ensures that the given file path is not null nor empty and not an external reference
	 *	<ol>
	 *	<li>All backslashes "\" are exchanged by slashes "/"</li>
	 *  <li>Any substring "/../", "/./" or "//" will be removed</li>
	 *	<li>A prefix "./" and "../" will be removed</li>
	 *  </ol>
	 *
	 * @throws IllegalArgumentException If the path is NULL, empty or an external path (e.g. starting with "../" is given).
	 *  None relative URLs will NOT throw an exception.
	 * @return the normalized path or the URL
	 */
	static String normalizeFilePath(String internalPath) {
		if (internalPath.equals(EMPTY_STRING)) {
			String errMsg = "The internalPath given by parameter is an empty string!";
			Logger.getLogger(OdfPackage.class.getName()).severe(errMsg);
			throw new IllegalArgumentException(errMsg);
		} else {
			return normalizePath(internalPath);
		}
	}

	/**
	 * Ensures the given directory path is not null nor an external reference to resources outside the package.
	 * An empty path and slash "/" are both mapped to the root directory/document.
	 *
	 * NOTE: Although ODF only refer the "/" as root,
	 * the empty path aligns more adequate with the file system concept.
	 *
	 * To ensure the given directory path within the package can be used as a key (is unique for the Package) the path will be normalized.
	 * @see #normalizeFilePath(String)
	 * In addition to the file path normalization a trailing slash will be used for directories.
	 */
	static String normalizeDirectoryPath(String directoryPath) {
		directoryPath = normalizePath(directoryPath);
		// if not the root document - which is from ODF view a '/' and no
		// trailing '/'
		if (!directoryPath.equals(OdfPackageDocument.ROOT_DOCUMENT_PATH)
				&& !directoryPath.endsWith(SLASH)) {
			// add a trailing slash
			directoryPath = directoryPath + SLASH;
		}
		return directoryPath;
	}

	/** Normalizes both directory and file path */
	static String normalizePath(String path) {
		if (path == null) {
			String errMsg = "The internalPath given by parameter is NULL!";
			Logger.getLogger(OdfPackage.class.getName()).severe(errMsg);
			throw new IllegalArgumentException(errMsg);
		} else if (!mightBeExternalReference(path)) {
			if (path.equals(EMPTY_STRING)) {
				path = SLASH;
			} else {
				// exchange all backslash "\" with a slash "/"
				if (path.indexOf('\\') != -1) {
					path = BACK_SLASH_PATTERN.matcher(path).replaceAll(SLASH);
				}
				// exchange all double slash "//" with a slash "/"
				while (path.indexOf("//") != -1) {
					path = DOUBLE_SLASH_PATTERN.matcher(path).replaceAll(SLASH);
				}
				// if directory replacements (e.g. ..) exist, resolve and remove them
				if (path.indexOf("/.") != -1 || path.indexOf("./") != -1) {
					path = removeChangeDirectories(path);
				}
			}
		}
		return path;
	}

	/** Normalizes both directory and file path */
	private static boolean mightBeExternalReference(String internalPath) {
		boolean isExternalReference = false;
		// if the fileReference is a external relative documentURL..
		if (internalPath.startsWith(DOUBLE_DOT)
				|| // or absolute documentURL AND not root document
				internalPath.startsWith(SLASH) && !internalPath.equals(SLASH)
				|| // or absolute IRI
				internalPath.contains(COLON)) {
			isExternalReference = true;
		}
		return isExternalReference;
	}

	/** Resolving the directory replacements (ie. "/../" and "/./") with a slash "/" */
	private static String removeChangeDirectories(String path) {
		boolean isDirectory = path.endsWith(SLASH);
		StringTokenizer tokenizer = new StringTokenizer(path, SLASH);
		int tokenCount = tokenizer.countTokens();
		List<String> tokenList = new ArrayList<String>(tokenCount);
		// add all paths to a list
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			tokenList.add(token);
		}
		if (!isDirectory) {
			String lastPath = tokenList.get(tokenCount - 1);
			if (lastPath.equals(DOT) || lastPath.equals(DOUBLE_DOT)) {
				isDirectory = true;
			}
		}
		String currentToken;
		int removeDirLevel = 0;
		StringBuilder out = new StringBuilder();
		// work on the list from back to front
		for (int i = tokenCount - 1; i >= 0; i--) {
			currentToken = tokenList.get(i);
			// every ".." will remove an upcoming path
			if (currentToken.equals(DOUBLE_DOT)) {
				removeDirLevel++;
			} else if (currentToken.equals(DOT)) {
			} else {
				// if a path have to be remove, neglect current path
				if (removeDirLevel > 0) {
					removeDirLevel--;
				} else {
					// add the path segment
					out.insert(0, SLASH);
					out.insert(0, currentToken);
				}
			}
		}
		if (removeDirLevel > 0) {
			return EMPTY_STRING;
		} else {
			if (!isDirectory) {
				// remove trailing slash /
				out.deleteCharAt(out.length() - 1);
			}
			return out.toString();
		}
	}

	/**
	 * Checks if the given reference is a reference, which points outside the
	 * ODF package
	 *
	 * @param internalPath
	 *            the file reference to be checked
	 * @return true if the reference is an package external reference
	 */
	public static boolean isExternalReference(String internalPath) {
		if(mightBeExternalReference(internalPath)){
			return true;
		}else{
			return mightBeExternalReference(normalizePath(internalPath));
		}
	}

	/**
	 * Allow an application to register an error event handler.
	 *
	 * <p>If the application does not register an error handler, all
	 * error events reported by the ODFDOM (e.g. the SAX Parser) will be silently
	 * ignored; however, normal processing may not continue.  It is
	 * highly recommended that all ODF applications implement an
	 * error handler to avoid unexpected bugs.</p>
	 *
	 * <p>Applications may register a new or different handler in the
	 * middle of a parse, and the ODFDOM will begin using the new
	 * handler immediately.</p>
	 *
	 * @param handler The error handler.
	 * @see #getErrorHandler
	 */
	public void setErrorHandler(ErrorHandler handler) {
		mErrorHandler = handler;
	}

	/**
	 * Return the current error handler used for ODF validation.
	 *
	 * @return The current error handler, or null if none
	 *         has been registered and validation is disabled.
	 * @see #setErrorHandler
	 */
	public ErrorHandler getErrorHandler() {
		return mErrorHandler;
	}

	private void logValidationWarning(ValidationConstraint constraint, String baseURI, Object... o) {
		try {
			int varCount = 0;
			if (o != null) {
				varCount = o.length;
			}
			switch (varCount) {
				case 0:
					mErrorHandler.warning(new OdfValidationException(constraint, baseURI, o));
					break;
				case 1:
					mErrorHandler.warning(new OdfValidationException(constraint, baseURI, o[0]));
					break;
				case 2:
					mErrorHandler.warning(new OdfValidationException(constraint, baseURI, o[0], o[1]));
					break;
			}
		} catch (SAXException ex) {
			Logger.getLogger(OdfPackage.class.getName()).log(Level.WARNING, null, ex);
		}
	}

	private void logValidationError(ValidationConstraint constraint, String baseURI, Object... o) {
		try {
			int varCount = 0;
			if (o != null) {
				varCount = o.length;
			}
			switch (varCount) {
				case 0:
					mErrorHandler.error(new OdfValidationException(constraint, baseURI, o));
					break;
				case 1:
					mErrorHandler.error(new OdfValidationException(constraint, baseURI, o[0]));
					break;
				case 2:
					mErrorHandler.error(new OdfValidationException(constraint, baseURI, o[0], o[1]));
					break;
			}
		} catch (SAXException ex) {
			Logger.getLogger(OdfPackage.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
