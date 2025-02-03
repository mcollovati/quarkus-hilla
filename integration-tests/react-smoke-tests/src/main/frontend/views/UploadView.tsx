import {useState} from 'react';

import {ViewConfig} from "@vaadin/hilla-file-router/types.js";
//import {UploadEndpoint} from "Frontend/generated/endpoints";

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
            uploadFile(file);
        }
    };
    const uploadFile = (file: File) => {

        // const file = new FormData(event.target as HTMLFormElement).get("file");
        // const savedFile = await UploadEndpoint.upload("my-test", file);

        // Temporary upload logic until Hilla completes multipart form
        // support on the client side services
        const formData = new FormData();
        formData.append("/file", file);
        formData.append("hilla_body_part", `{ "name": "my-test" }`);

        const csrfCookie = "csrfToken";
        const crsfToken = document.cookie.split(";")
            .map((c) => c.trim())
            .filter(c => c.startsWith(csrfCookie + "="))
            .map(c => c.substring(csrfCookie.length +1))[0];
        const xhr = new XMLHttpRequest();
        xhr.withCredentials = true;
        xhr.open('POST', '/connect/UploadEndpoint/upload', true);
        xhr.setRequestHeader("X-CSRF-Token", crsfToken);
        // Set up event listeners for progress, completion, etc.
        xhr.upload.onprogress = (event) => {
            if (event.lengthComputable) {
                const percentComplete = (event.loaded / event.total) * 100;
                console.log(`Upload progress: ${percentComplete}%`);
            }
        };
        xhr.onload = () => {
            if (xhr.status === 200) {
                console.log('File uploaded successfully!');
                console.log('Response:', xhr.responseText);
                setUploadState("File saved to " + xhr.responseText);
            } else {
                console.error('File upload failed.');
            }
        };
        xhr.onerror = () => {
            console.error('Error during file upload.');
        };
        // Send the request
        xhr.send(formData);

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