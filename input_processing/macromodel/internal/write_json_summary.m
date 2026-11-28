function normalized_summary = write_json_summary(summary, output_file, options)
% WRITE_JSON_SUMMARY  Write a documentation summary as formatted JSON.
%
% Usage:
%   write_json_summary(summary, 'output/documentation_data/ramsey/ramsey_summary_Poland.json')
%
% The helper ensures the output directory exists, injects basic metadata,
% and writes through a temporary file before replacing the target.

    arguments
        summary (1,1) struct
        output_file (1,:) char
        options.country (1,:) char = ''
        options.source_function (1,:) char = ''
        options.pretty_print (1,1) logical = true
    end

    normalized_summary = summary;

    if ~isfield(normalized_summary, 'metadata') || ~isstruct(normalized_summary.metadata)
        normalized_summary.metadata = struct();
    end

    if ~isfield(normalized_summary.metadata, 'country') || isempty(normalized_summary.metadata.country)
        if ~isempty(options.country)
            normalized_summary.metadata.country = options.country;
        end
    end

    if ~isfield(normalized_summary.metadata, 'source_function') || isempty(normalized_summary.metadata.source_function)
        if ~isempty(options.source_function)
            normalized_summary.metadata.source_function = options.source_function;
        end
    end

    normalized_summary.metadata.generated_at = char(datetime('now', ...
        'Format', 'yyyy-MM-dd''T''HH:mm:ss'));

    output_dir = fileparts(output_file);
    if ~isempty(output_dir) && ~exist(output_dir, 'dir')
        mkdir(output_dir);
    end

    try
        json_text = jsonencode(normalized_summary, 'PrettyPrint', options.pretty_print);
    catch
        json_text = jsonencode(normalized_summary);
    end

    temp_file = [tempname(output_dir) '.json'];
    file_id = fopen(temp_file, 'w');
    if file_id == -1
        error('Could not open temporary file for writing: %s', temp_file);
    end

    try
        fwrite(file_id, json_text, 'char');
        fwrite(file_id, newline, 'char');
        fclose(file_id);

        if exist(output_file, 'file')
            delete(output_file);
        end

        movefile(temp_file, output_file, 'f');
    catch write_error
        fclose_if_open(file_id);
        if exist(temp_file, 'file')
            delete(temp_file);
        end
        rethrow(write_error);
    end
end

function fclose_if_open(file_id)
    if isnumeric(file_id) && isscalar(file_id) && file_id > 0
        try
            fclose(file_id);
        catch
        end
    end
end
