package net.amygdalum.stringsearchalgorithms.patternsearch.chars;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import net.amygdalum.regexparser.RegexNode;
import net.amygdalum.regexparser.RegexParser;
import net.amygdalum.regexparser.RegexParserOption;
import net.amygdalum.stringsearchalgorithms.search.StringMatch;
import net.amygdalum.util.bits.BitSet;
import net.amygdalum.util.io.CharProvider;
import net.amygdalum.util.io.StringCharProvider;

public class GlushkovPrefixExtender implements FactorExtender {

	private String pattern;
	private GlushkovAutomaton automaton;
	private int minLength;

	private int prefixLength;
	private BitSet prefixInitial;

	public GlushkovPrefixExtender(String pattern, RegexParserOption... options) {
		RegexNode root = parseAndNormalizeRegex(pattern, options);
		GlushkovAnalyzer analyzer = new GlushkovAnalyzer(root).analyze();
		this.pattern = pattern;
		this.automaton = analyzer.buildAutomaton();
		this.minLength = analyzer.minLength();
	}

	private GlushkovPrefixExtender(String pattern, GlushkovAutomaton automaton, int minLength, int prefixLength, BitSet prefixInitial) {
		this.pattern = pattern;
		this.automaton = automaton;
		this.minLength = minLength;
		this.prefixLength = prefixLength;
		this.prefixInitial = prefixInitial;
	}

	private static RegexNode parseAndNormalizeRegex(String pattern, RegexParserOption... options) {
		RegexParser parser = new RegexParser(pattern, options);
		RegexNode root = parser.parse();
		return root.accept(new GlushkovNormalizer());
	}

	public GlushkovPrefixExtender forFactor(String prefix) {
		BitSet prefixInitial = match(automaton.getInitial(), new StringCharProvider(prefix, 0));
		return new GlushkovPrefixExtender(pattern, automaton, minLength, prefix.length(), prefixInitial);
	}

	@Override
	public String getPattern() {
		return pattern;
	}

	@Override
	public int getPatternLength() {
		return minLength;
	}

	@Override
	public List<String> getBestFactors(int max) {
		return new ArrayList<>(getPrefixes(max));
	}

	@Override
	public boolean hasFactor(String factor) {
		return getPrefixes(factor.length()).contains(factor);
	}

	public Set<String> getPrefixes(int max) {
		return getPrefixes(automaton.getInitial(), 1, max);
	}

	private Set<String> getPrefixes(BitSet state, int min, int max) {
		Set<String> prefixes = new LinkedHashSet<String>();
		if (min <= 0 && automaton.isFinal(state)) {
			prefixes.add("");
			return prefixes;
		} else if (max <= 0) {
			prefixes.add("");
			return prefixes;
		}
		for (char c : automaton.supportedChars()) {
			BitSet next = automaton.next(state, c);
			if (!next.isEmpty()) {
				Set<String> subPrefixes = getPrefixes(next, min - 1, max - 1);
				for (String subPrefix : subPrefixes) {
					prefixes.add(c + subPrefix);
				}
			}
		}
		return prefixes;
	}

	@Override
	public SortedSet<StringMatch> extendFactor(CharProvider chars, boolean longest) {
		MatchBuilder listener = new MatchBuilder(longest);
		match(prefixInitial, chars, listener);
		return listener.getMatches();
	}

	private BitSet match(BitSet state, CharProvider chars, MatchListener... listeners) {
		boolean notify = listeners != null && listeners.length > 0;
		long pos = chars.current();
		long start = pos - this.prefixLength;
		while (!chars.finished() && !state.isEmpty()) {
			if (notify && automaton.isFinal(state)) {
				long end = chars.current();
				for (MatchListener listener : listeners) {
					listener.notify(start, end, chars);
				}
			}
			char c = chars.next();
			state = automaton.next(state, c);
		}
		if (notify && chars.finished() && automaton.isFinal(state)) {
			long end = chars.current();
			for (MatchListener listener : listeners) {
				listener.notify(start, end, chars);
			}
		}
		chars.move(pos);
		return state;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	public static class Factory implements FactorExtenderFactory {

		private RegexParserOption[] options;

		public Factory(RegexParserOption... options) {
			this.options = options;
		}

		@Override
		public FactorExtender of(String pattern) {
			return new GlushkovPrefixExtender(pattern, options);
		}

	}
}
