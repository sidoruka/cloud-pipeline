ALTER TABLE PIPELINE.DATASTORAGE ADD FOLDER_ID BIGINT NULL;
ALTER TABLE PIPELINE.DATASTORAGE ADD CONSTRAINT datastorage_folder_id_fk FOREIGN KEY (folder_id) REFERENCES folder (FOLDER_ID);