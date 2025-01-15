import {ViewConfig} from '@vaadin/hilla-file-router/types.js';
import {useSignal} from '@vaadin/hilla-react-signals';
import {Button, Notification, TextField} from '@vaadin/react-components';
import {HelloWorldService} from 'Frontend/generated/endpoints.js';

export const config: ViewConfig = {
    menu: {order: 1, icon: 'line-awesome/svg/lock-solid.svg'},
    title: 'Hilla - Authenticated',
    loginRequired: true,
};

export default function HillaAuthenticatedView() {
    const name = useSignal('');

    return (
        <div className="flex flex-col h-full items-center justify-center p-l text-center box-border">
            <img style={{width: '200px'}} src="images/empty-plant.png"/>
            <h2>Hilla - Authenticated</h2>
            <p>All authenticated users can see this page</p>

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
        </div>
    );
}
