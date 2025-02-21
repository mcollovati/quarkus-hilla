import {useState} from 'react';

import {ViewConfig} from "@vaadin/hilla-file-router/types.js";
import {UploadEndpoint} from "Frontend/generated/endpoints";

export const config: ViewConfig = {
    title: "Upload",
    route: "upload"
};

export default function UploadView() {
    const [file, setFile] = useState<File | null>(null);
    const [uploadState, setUploadState] = useState("");

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        if (event.target.files && event.target.files[0]) {
            setFile(event.target.files[0]);
        }
    };

    // Handle form submission
    const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault(); // Prevent the default form submission behavior
        if (file) {
            UploadEndpoint.upload("my-test", file).then(
                savedFile => {
                    setUploadState(`File saved to "${savedFile}"`);
                    setFile(null);
                },
                err => console.log(err)
            );

        }
    };

    return (<form id="upload" onSubmit={handleSubmit}>
        <div>
            <input type="file" onChange={handleFileChange}/>
        </div>
        <div>
            <button id="submit-upload" type="submit" disabled={!file}>Upload</button>
        </div>
        <output id="out">{uploadState}</output>
    </form>);
}