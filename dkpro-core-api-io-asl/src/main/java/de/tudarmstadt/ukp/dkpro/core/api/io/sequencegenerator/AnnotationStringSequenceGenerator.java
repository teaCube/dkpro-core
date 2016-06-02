/*******************************************************************************
 * Copyright 2016
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.api.io.sequencegenerator;

import de.tudarmstadt.ukp.dkpro.core.api.featurepath.FeaturePathException;
import de.tudarmstadt.ukp.dkpro.core.api.featurepath.FeaturePathFactory;
import de.tudarmstadt.ukp.dkpro.core.api.featurepath.FeaturePathUtils;
import de.tudarmstadt.ukp.dkpro.core.api.io.TextUtils;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;

import java.io.IOException;
import java.util.*;

/**
 * Create token sequences from JCas annotations.
 * <p>
 * Either create a single token sequence from the whole document, or multiple sequences based on
 * covering annotations, e.g. one sequence for each sentence.
 * <p>
 * By default, the sequences are created from {@link Token}s found in the input document. In order to use
 * other annotations, e.g. lemmas, specify the feature path in {@link Builder#featurePath(String)}.
 */
public class AnnotationStringSequenceGenerator
        extends StringSequenceGenerator
{
    public static final String NONE_LABEL = "X"; // some label has to be set for Mallet instances
    private String featurePath = Token.class.getCanonicalName();
    /**
     * ignore tokens that are shorter than this value. If <= 0 or negative, filter nothing.
     */
    private int minTokenLength = 0;
    private Set<String> stopwords = Collections.emptySet();
    private String stopwordReplacement = "";
    private String filterRegex = "";
    private String filterRegexReplacement = "";

    private AnnotationStringSequenceGenerator(Builder builder)
            throws IOException
    {
        this.minTokenLength = builder.minTokenLength;
        this.featurePath = builder.featurePath;
        if (!builder.stopwordsFile.isEmpty()) {
            this.stopwords = TextUtils.readStopwordsFile(builder.stopwordsFile, isLowercase());
        }
        this.stopwordReplacement = builder.stopwordsReplacement;
        this.filterRegex = builder.filterRegex;
        this.filterRegexReplacement = builder.filterRegexReplacement;
    }

    @Override
    public List<String[]> tokenSequences(JCas aJCas)
            throws FeaturePathException
    {
        List<String[]> tokenSequences = new ArrayList<>();
        if (getCoveringTypeName().isPresent()) {
            Type coveringType = aJCas.getTypeSystem().getType(getCoveringTypeName().get());

            /* iterate over covering annotations */
            for (AnnotationFS covering : CasUtil.select(aJCas.getCas(), coveringType)) {
                tokenSequences.add(annotationSequence(aJCas, Optional.of(covering)));
            }
        }
        else {
            /* add a single token sequence for the whole document */
            tokenSequences
                    .add(annotationSequence(aJCas, Optional.empty()));
        }
        return tokenSequences;
    }

    /**
     * Generate a string array of all features (e.g. tokens or lemmas) covered by an
     * annotation (e.g. a sentence). If no coveringAnnotation is given (i.e. null), return all
     * features in the CAS.
     *
     * @param aJCas              a {@link JCas}
     * @param coveringAnnotation an Optional covering annotation from which tokens are selected, e.g. a {@link Sentence}
     * @return a string array representing all extracted tokens
     * @throws FeaturePathException if the annotation type specified in PARAM_TOKEN_FEATURE_PATH cannot be extracted.
     */
    private String[] annotationSequence(JCas aJCas, Optional<AnnotationFS> coveringAnnotation)
            throws FeaturePathException
    {
        List<String> tokenSequence = new ArrayList<>();

        FeaturePathFactory.FeaturePathIterator<AnnotationFS> valueIterator =
                FeaturePathUtils.featurePathIterator(aJCas, featurePath, coveringAnnotation);

        /* iterate over tokens (optionally within covering annotation) */
        while (valueIterator.hasNext()) {
            String token = valueIterator.next().getValue();
            if (token.length() >= minTokenLength) {
                if (isLowercase()) {
                    token = token.toLowerCase();
                }
                if (stopwords.contains(token)) {
                    token = stopwordReplacement;
                }
                if (!filterRegex.isEmpty() && token.matches(filterRegex)) {
                    token = filterRegexReplacement;
                }
                if (!token.isEmpty()) {
                    tokenSequence.add(token);
                }
            }
        }
        return tokenSequence.toArray(new String[tokenSequence.size()]);
    }

    /**
     * Builder for {@link AnnotationStringSequenceGenerator} instances.
     */
    public static class Builder
    {
        private int minTokenLength = 0;
        private String stopwordsFile = "";
        private String stopwordsReplacement = "";
        private String featurePath = Token.class.getCanonicalName();
        private String filterRegex;
        private String filterRegexReplacement;

        /**
         * @param featurePath set the feature path to use for creating token sequences.
         * @return a {@link Builder}
         */
        public Builder featurePath(String featurePath)
        {
            this.featurePath = featurePath;
            return this;
        }

        /**
         * @param stopwordsFile set the location of the stopwords file
         * @return a {@link Builder}
         */
        public Builder stopwordsFile(String stopwordsFile)
        {
            this.stopwordsFile = stopwordsFile;
            return this;
        }

        /**
         * @param stopwordsReplacement stopwords are replaced by this string or removed if replacement string is empty
         * @return a {@link Builder}
         */
        public Builder stopwordsReplacement(String stopwordsReplacement)
        {
            this.stopwordsReplacement = stopwordsReplacement;
            return this;
        }

        /**
         * @param minTokenLength tokens shorter than the given length are filtered out
         * @return a {@link Builder}
         */
        public Builder minTokenLength(int minTokenLength)
        {
            this.minTokenLength = minTokenLength;
            return this;
        }

        /**
         * @param filterRegex Tokens matching this regular expression are filtered out.
         * @return a {@link Builder}
         */
        public Builder filterRegex(String filterRegex)
        {
            this.filterRegex = filterRegex;
            return this;
        }

        /**
         * @param filterRegexReplacement tokens matching the {@link #filterRegex} are replaced by this string. If this is empty, these tokens are removed.
         * @return a {@link Builder}
         */
        public Builder filterRegexReplacement(String filterRegexReplacement)
        {
            this.filterRegexReplacement = filterRegexReplacement;
            return this;
        }

        /**
         * Generate a {@link AnnotationStringSequenceGenerator}
         *
         * @return a {@link AnnotationStringSequenceGenerator} instance
         * @throws IOException if the stopwords file is specified and cannot be read
         */
        public AnnotationStringSequenceGenerator build()
                throws IOException
        {
            return new AnnotationStringSequenceGenerator(this);
        }
    }
}