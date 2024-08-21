import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { Button } from '@vaadin/react-components/Button.js';
import { Notification } from '@vaadin/react-components/Notification.js';
import { TextField } from '@vaadin/react-components/TextField.js';
import { HelloWorldService } from 'Frontend/generated/endpoints.js';
import { useState } from 'react';

export const config: ViewConfig = {
    menu: {
        title: "Main page"
    }
};
export default function HelloWorldView() {
  const [name, setName] = useState('');

  return (
    <>
      <section className="flex p-m gap-m items-end">
        <TextField
          label="Your name"
          onValueChanged={(e) => {
            setName(e.detail.value);
          }}
        />
        <Button
          onClick={async () => {
            const serverResponse = await HelloWorldService.sayHello(name);
            Notification.show(serverResponse);
          }}
        >
          Say hello
        </Button>
      </section>
    </>
  );
}
