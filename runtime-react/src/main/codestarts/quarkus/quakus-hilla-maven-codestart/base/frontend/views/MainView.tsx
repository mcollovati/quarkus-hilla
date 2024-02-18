import { Button } from "@vaadin/react-components/Button.js";
import { Notification } from "@vaadin/react-components/Notification.js";
import { TextField } from "@vaadin/react-components/TextField.js";
import { HelloEndpoint } from "Frontend/generated/endpoints.js";
import { useState } from "react";

export default function MainView() {
  const [name, setName] = useState("");

  return (
    <>
      <TextField
        label="Your name"
        onValueChanged={(e) => {
          setName(e.detail.value);
        }}
      />
      <Button
        onClick={async () => {
          const serverResponse = await HelloEndpoint.sayHello(name);
          Notification.show(serverResponse);
        }}
      >
        Say hello
      </Button>
    </>
  );
}
