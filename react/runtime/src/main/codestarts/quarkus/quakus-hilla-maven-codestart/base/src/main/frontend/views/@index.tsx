import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { useSignal } from '@vaadin/hilla-react-signals';
import { Button, Notification, TextField } from '@vaadin/react-components';
import { HelloWorldService } from 'Frontend/generated/endpoints.js';

export const config: ViewConfig = {
  menu: { order: 0, icon: 'line-awesome/svg/globe-solid.svg' },
  title: 'Hello Hilla',
};

export default function HelloHillaView() {
  const name = useSignal('');

  return (
    <>
      <section className="flex p-m gap-m items-end">
        <TextField
          label="Your name"
          onValueChanged={(e) => {
            name.value = e.detail.value;
          }}
        />
        <Button
          onClick={async () => {
            const serverResponse = await HelloWorldService.sayHello(name.value);
            Notification.show(serverResponse);
          }}
        >
          Say hello
        </Button>
      </section>
    </>
  );
}
