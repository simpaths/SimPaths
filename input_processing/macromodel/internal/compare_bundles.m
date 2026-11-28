function result = compare_bundles(dir_a, dir_b, options)
% COMPARE_BUNDLES  Do two export bundles agree numerically within a tolerance?
%
%   r = compare_bundles(committed_dir, regenerated_dir, 'rel_tol', 1e-6)
%   compare_bundles(a, b, 'fail', true)   throw compare_bundles:Different
%   compare_bundles(a, b, 'exclude', '_macroScenario_*.csv')   drop matching names on both sides
%
% Byte identity is the wrong test for a regenerated bundle: the batch-run
% notes show fourth-digit noise across machines. So CSVs and JSON are compared
% number by number with |x-y| <= abs_tol + rel_tol*max(|x|,|y|) (an infinity
% only agrees with the same infinity), text columns and JSON strings must be
% equal, JSON arrays (of numbers, objects or mixed) element by element with
% their lengths, and a CSV's '#' comment lines as exact text. Other files
% (SIMPATHS_INTERFACE.md, BUNDLE_PROVENANCE.txt) are compared line by line: the
% decimal numbers in a line under the same tolerance, integers and the text
% around them exactly. Ignored there: generation
% stamps, the dynare and matlab lines of BUNDLE_PROVENANCE.txt (another toolchain
% is not a difference, and a bundle without the lines is not one either), and
% the hex of `sha256` lines (kind and path are compared; the Java
% provenance test owns the digests, which noise in a regenerated datafile
% changes). A file present on one side only, a different header, a different
% row or line count or a NaN facing a number are differences: the comparison
% never passes vacuously.

    arguments
        dir_a (1,:) char
        dir_b (1,:) char
        options.rel_tol (1,1) double = 1e-6
        options.abs_tol (1,1) double = 1e-12
        options.fail (1,1) logical = false
        options.exclude (1,:) char = ''
    end

    diffs = {};
    names_a = listing(dir_a, options.exclude);
    names_b = listing(dir_b, options.exclude);
    for n = setdiff(names_a, names_b), diffs{end+1} = sprintf('%s only in %s', n{1}, dir_a); end %#ok<AGROW>
    for n = setdiff(names_b, names_a), diffs{end+1} = sprintf('%s only in %s', n{1}, dir_b); end %#ok<AGROW>

    common = intersect(names_a, names_b);
    for i = 1:numel(common)
        name = common{i};
        fa = fullfile(dir_a, name); fb = fullfile(dir_b, name);
        [~, ~, ext] = fileparts(name);
        switch lower(ext)
            case '.csv',  d = compare_csv(fa, fb, name, options);
            case '.json', d = compare_json(jsondecode(fileread(fa)), jsondecode(fileread(fb)), name, options);
            otherwise,    d = compare_text(fa, fb, name, options);
        end
        diffs = [diffs d]; %#ok<AGROW>
    end

    result = struct('ok', isempty(diffs), 'differences', {diffs}, 'files_compared', numel(common));
    if options.fail && ~result.ok
        error('compare_bundles:Different', 'Bundles differ:\n  %s', strjoin(diffs, sprintf('\n  ')));
    end
end

function names = listing(d, exclude)
    L = dir(d); L = L(~[L.isdir]);
    names = sort({L.name});
    if ~isempty(exclude)
        names = names(cellfun(@isempty, regexp(names, ['^' regexptranslate('wildcard', exclude) '$'], 'once')));
    end
end

function d = compare_csv(fa, fb, name, options)
    d = {};
    % '#' lines are comments (the scenario CSVs open with a block of them); left to
    % readtable's detection, the '=' in them is taken for the delimiter.
    if ~isequal(comment_lines(fa), comment_lines(fb))
        d{end+1} = sprintf('%s: comment lines differ', name);
    end
    ta = readtable(fa, 'Delimiter', ',', 'CommentStyle', '#', 'TextType', 'string', 'VariableNamingRule', 'preserve');
    tb = readtable(fb, 'Delimiter', ',', 'CommentStyle', '#', 'TextType', 'string', 'VariableNamingRule', 'preserve');
    if ~isequal(ta.Properties.VariableNames, tb.Properties.VariableNames)
        d{end+1} = sprintf('%s: columns differ', name); return;
    end
    if height(ta) ~= height(tb)
        d{end+1} = sprintf('%s: rows differ (%d vs %d)', name, height(ta), height(tb)); return;
    end
    for v = ta.Properties.VariableNames
        xa = ta.(v{1}); xb = tb.(v{1});
        if isnumeric(xa) && isnumeric(xb)
            bad = ~within(xa, xb, options);
            if any(bad)
                k = find(bad, 1);
                d{end+1} = sprintf('%s: column %s row %d: %.10g vs %.10g', name, v{1}, k, xa(k), xb(k)); %#ok<AGROW>
            end
        elseif ~isequaln(xa, xb)   % isequal calls <missing> unequal to itself
            d{end+1} = sprintf('%s: column %s text differs', name, v{1}); %#ok<AGROW>
        end
    end
end

function d = compare_json(a, b, where, options)
    d = {};
    if isstruct(a) && isstruct(b)
        fa = sort(fieldnames(a)); fb = sort(fieldnames(b));
        if ~isequal(fa, fb), d{end+1} = sprintf('%s: keys differ', where); return; end
        % A JSON array of objects with the same keys decodes to a struct array.
        if numel(a) ~= numel(b)
            d{end+1} = sprintf('%s: array length differs (%d vs %d)', where, numel(a), numel(b)); return;
        end
        for k = 1:numel(a)
            at = where;
            if numel(a) > 1, at = sprintf('%s(%d)', where, k); end
            for f = fa'
                d = [d compare_json(a(k).(f{1}), b(k).(f{1}), [at '.' f{1}], options)]; %#ok<AGROW>
            end
        end
    elseif iscell(a) && iscell(b)
        % Ragged arrays and arrays of mixed objects decode to cells.
        if ~isequal(size(a), size(b))
            d{end+1} = sprintf('%s: array length differs (%d vs %d)', where, numel(a), numel(b)); return;
        end
        for k = 1:numel(a)
            d = [d compare_json(a{k}, b{k}, sprintf('%s{%d}', where, k), options)]; %#ok<AGROW>
        end
    elseif isnumeric(a) && isnumeric(b)
        if ~isequal(size(a), size(b)) || any(~within(a(:), b(:), options))
            d{end+1} = sprintf('%s: numeric value differs', where);
        end
    elseif ~isequal(a, b)
        d{end+1} = sprintf('%s: value differs', where);
    end
end

function d = compare_text(fa, fb, name, options)
    d = {};
    [la, ia] = strip_stamps(fileread(fa)); lb = strip_stamps(fileread(fb));
    if numel(la) ~= numel(lb)
        d{end+1} = sprintf('%s: line count differs (%d vs %d, stamps ignored)', name, numel(la), numel(lb)); return;
    end
    number = '-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?';
    bad = {};
    for i = 1:numel(la)
        [na, ta] = regexp(la{i}, number, 'match', 'split');
        [nb, tb] = regexp(lb{i}, number, 'match', 'split');
        if ~isequal(ta, tb)
            bad{end+1} = sprintf('line %d text differs', ia(i)); %#ok<AGROW>
        else
            % Integers (a date stamp, a count) must match exactly: under a relative
            % tolerance, 1e6 or more differing by 1 would pass.
            ints = is_integer_token(na) & is_integer_token(nb);
            k = find((ints & ~strcmp(na, nb)) | (~ints & ~within(str2double(na), str2double(nb), options)), 1);
            if ~isempty(k), bad{end+1} = sprintf('line %d: %s vs %s', ia(i), na{k}, nb{k}); end %#ok<AGROW>
        end
    end
    if ~isempty(bad)
        d{end+1} = sprintf('%s: %s (%d line(s) differ, stamps ignored)', name, bad{1}, numel(bad));
    end
end

function [lines, idx] = strip_stamps(txt)
    lines = strsplit(strrep(txt, char(13), ''), newline, 'CollapseDelimiters', false);
    lines = regexprep(lines, '^(sha256\s+\S+\s+)\S+', '$1<digest>');
    idx = find(~contains(lines, 'Generated:') & ~startsWith(lines, {'exported_at', 'dynare ', 'matlab '}));
    lines = lines(idx);
end

function ok = within(x, y, options)
    both_nan = isnan(x) & isnan(y);
    % An infinity agrees only with the same infinity: abs(Inf - Inf) is NaN, and
    % Inf <= rel_tol * Inf would let Inf pass against -Inf or any number.
    inf_any = isinf(x) | isinf(y);
    near = abs(x - y) <= options.abs_tol + options.rel_tol * max(abs(x), abs(y));
    ok = both_nan | (inf_any & x == y) | (~inf_any & near);
    ok(isnan(x) ~= isnan(y)) = false;
end

function tf = is_integer_token(tokens)
    tf = ~cellfun(@isempty, regexp(tokens, '^-?\d+$', 'once'));
end

function lines = comment_lines(file)
    lines = strsplit(strrep(fileread(file), char(13), ''), newline);
    lines = lines(startsWith(lines, '#'));
end
