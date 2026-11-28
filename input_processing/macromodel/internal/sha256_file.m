function hex = sha256_file(file, options)
% SHA256_FILE  Lower-case hex SHA-256 of a file.
%
%   sha256_file(f)                 text mode: 0x0D (CR) bytes dropped before hashing
%   sha256_file(f, 'text', false)  raw bytes
%
% Text mode exists because SimPathsEU is checked out with core.autocrlf=true on
% Windows and with LF on Linux CI; the hash has to name the content, not the
% checkout. BundleProvenanceTest.java applies the same rule and the two must
% stay in step (shared known digest in tests/test_bundle_provenance.m).

    arguments
        file (1,:) char
        options.text (1,1) logical = true
    end

    fid = fopen(file, 'r');
    if fid < 0
        error('sha256_file:CannotOpen', 'Cannot open %s', file);
    end
    bytes = fread(fid, Inf, 'uint8=>uint8');
    fclose(fid);

    if options.text
        bytes = bytes(bytes ~= 13);
    end

    % Java byte[] is signed; typecast keeps the bit pattern, a plain uint8
    % argument would saturate values above 127. An empty int8 binds to the
    % update(byte) overload and errors, so an empty (or all-CR) file skips the
    % update and hashes to the SHA-256 of nothing, as in Java.
    md = java.security.MessageDigest.getInstance('SHA-256');
    if ~isempty(bytes)
        md.update(typecast(bytes(:), 'int8'));
    end
    digest = typecast(md.digest(), 'uint8');
    hex = lower(reshape(dec2hex(digest(:), 2)', 1, []));
end
