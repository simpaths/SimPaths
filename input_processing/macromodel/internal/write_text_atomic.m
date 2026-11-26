function write_text_atomic(output_file, text, options)
% WRITE_TEXT_ATOMIC  Write text to a file atomically, or fail loudly.
%
%   WRITE_TEXT_ATOMIC(file, text) writes `text` to a temporary file in the
%   destination directory and then moves it into place, so a consumer never
%   observes a partially written file. WRITE_TEXT_ATOMIC(file, text,
%   'verify_json', true) additionally re-reads the file and decodes it,
%   failing if what landed on disk is not valid JSON.
%
% CONTRACT: every JSON artefact consumed by the Java runtime must be written
% through here. Java string-scans these files for their keys, so a truncated
% tail does not raise a parse error there — the missing keys simply read as
% 0.0 and a run proceeds on silently wrong parameters. A bare
% fopen/fprintf/fclose has two ways to produce that: fopen returning -1 and
% going unchecked, and a crash or full disk between the first and last write.

    arguments
        output_file (1,:) char
        text (1,:) char
        options.verify_json (1,1) logical = false
        options.append_newline (1,1) logical = true
    end

    output_dir = fileparts(output_file);
    if ~isempty(output_dir) && ~exist(output_dir, 'dir')
        mkdir(output_dir);
    end
    if isempty(output_dir)
        output_dir = pwd;
    end

    temp_file = [tempname(output_dir) '.tmp'];
    file_id = fopen(temp_file, 'w');
    if file_id == -1
        error('write_text_atomic:CannotOpen', ...
              'Could not open temporary file for writing: %s', temp_file);
    end

    try
        count = fwrite(file_id, text, 'char');
        if count ~= numel(text)
            error('write_text_atomic:ShortWrite', ...
                  'Wrote %d of %d characters to %s.', count, numel(text), temp_file);
        end
        if options.append_newline
            fwrite(file_id, newline, 'char');
        end
        fclose(file_id);
        file_id = -1;

        if options.verify_json
            jsondecode(fileread(temp_file));
        end

        if exist(output_file, 'file')
            delete(output_file);
        end
        movefile(temp_file, output_file, 'f');
    catch write_error
        if file_id > 0
            try, fclose(file_id); catch, end
        end
        if exist(temp_file, 'file')
            delete(temp_file);
        end
        error('write_text_atomic:Failed', ...
              'Atomic write to %s failed: %s', output_file, write_error.message);
    end
end
