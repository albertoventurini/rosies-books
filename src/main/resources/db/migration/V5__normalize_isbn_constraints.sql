ALTER TABLE edition
    ADD CONSTRAINT edition_isbn_10_checksum CHECK (
        isbn_10 IS NULL OR mod(
            cast(substring(isbn_10, 1, 1) AS integer) * 10
          + cast(substring(isbn_10, 2, 1) AS integer) * 9
          + cast(substring(isbn_10, 3, 1) AS integer) * 8
          + cast(substring(isbn_10, 4, 1) AS integer) * 7
          + cast(substring(isbn_10, 5, 1) AS integer) * 6
          + cast(substring(isbn_10, 6, 1) AS integer) * 5
          + cast(substring(isbn_10, 7, 1) AS integer) * 4
          + cast(substring(isbn_10, 8, 1) AS integer) * 3
          + cast(substring(isbn_10, 9, 1) AS integer) * 2
          + CASE substring(isbn_10, 10, 1)
                WHEN 'X' THEN 10
                ELSE cast(substring(isbn_10, 10, 1) AS integer)
            END,
            11) = 0),
    ADD CONSTRAINT edition_isbn_13_checksum CHECK (
        isbn_13 IS NULL OR ((substring(isbn_13, 1, 3) IN ('978', '979')) AND mod(
            cast(substring(isbn_13, 1, 1) AS integer)
          + cast(substring(isbn_13, 2, 1) AS integer) * 3
          + cast(substring(isbn_13, 3, 1) AS integer)
          + cast(substring(isbn_13, 4, 1) AS integer) * 3
          + cast(substring(isbn_13, 5, 1) AS integer)
          + cast(substring(isbn_13, 6, 1) AS integer) * 3
          + cast(substring(isbn_13, 7, 1) AS integer)
          + cast(substring(isbn_13, 8, 1) AS integer) * 3
          + cast(substring(isbn_13, 9, 1) AS integer)
          + cast(substring(isbn_13, 10, 1) AS integer) * 3
          + cast(substring(isbn_13, 11, 1) AS integer)
          + cast(substring(isbn_13, 12, 1) AS integer) * 3
          + cast(substring(isbn_13, 13, 1) AS integer),
            10) = 0)),
    ADD CONSTRAINT edition_isbn_pair_consistent CHECK (
        isbn_10 IS NULL OR isbn_13 IS NULL
        OR substring(isbn_13, 1, 12) = '978' || substring(isbn_10, 1, 9));

ALTER TABLE user_edition_metadata_override
    ADD CONSTRAINT user_edition_metadata_override_isbn_10_checksum CHECK (
        isbn_10_value IS NULL OR mod(
            cast(substring(isbn_10_value, 1, 1) AS integer) * 10
          + cast(substring(isbn_10_value, 2, 1) AS integer) * 9
          + cast(substring(isbn_10_value, 3, 1) AS integer) * 8
          + cast(substring(isbn_10_value, 4, 1) AS integer) * 7
          + cast(substring(isbn_10_value, 5, 1) AS integer) * 6
          + cast(substring(isbn_10_value, 6, 1) AS integer) * 5
          + cast(substring(isbn_10_value, 7, 1) AS integer) * 4
          + cast(substring(isbn_10_value, 8, 1) AS integer) * 3
          + cast(substring(isbn_10_value, 9, 1) AS integer) * 2
          + CASE substring(isbn_10_value, 10, 1)
                WHEN 'X' THEN 10
                ELSE cast(substring(isbn_10_value, 10, 1) AS integer)
            END,
            11) = 0),
    ADD CONSTRAINT user_edition_metadata_override_isbn_13_checksum CHECK (
        isbn_13_value IS NULL
        OR ((substring(isbn_13_value, 1, 3) IN ('978', '979')) AND mod(
            cast(substring(isbn_13_value, 1, 1) AS integer)
          + cast(substring(isbn_13_value, 2, 1) AS integer) * 3
          + cast(substring(isbn_13_value, 3, 1) AS integer)
          + cast(substring(isbn_13_value, 4, 1) AS integer) * 3
          + cast(substring(isbn_13_value, 5, 1) AS integer)
          + cast(substring(isbn_13_value, 6, 1) AS integer) * 3
          + cast(substring(isbn_13_value, 7, 1) AS integer)
          + cast(substring(isbn_13_value, 8, 1) AS integer) * 3
          + cast(substring(isbn_13_value, 9, 1) AS integer)
          + cast(substring(isbn_13_value, 10, 1) AS integer) * 3
          + cast(substring(isbn_13_value, 11, 1) AS integer)
          + cast(substring(isbn_13_value, 12, 1) AS integer) * 3
          + cast(substring(isbn_13_value, 13, 1) AS integer),
            10) = 0));
